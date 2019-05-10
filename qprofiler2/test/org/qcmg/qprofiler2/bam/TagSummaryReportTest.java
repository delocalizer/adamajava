package org.qcmg.qprofiler2.bam;

import static org.junit.Assert.assertEquals;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.qcmg.common.util.XmlElementUtils;
import org.qcmg.qprofiler2.bam.TagSummaryReport2;
import org.qcmg.qprofiler2.util.XmlUtils;
import org.w3c.dom.Element;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMTagUtil;

public class TagSummaryReportTest {
	@Rule
	public  TemporaryFolder testFolder = new TemporaryFolder();
	protected String INPUT_FILE;
	final  SAMTagUtil STU = SAMTagUtil.getSingleton();
	
	@Before
	public void setup() throws IOException {
		INPUT_FILE = testFolder.newFile("input.sam").getAbsolutePath();
	}
	
	
	@Test
	public void simpleTest() throws Exception{
		
		TagSummaryReport2 report = new TagSummaryReport2();
		SAMRecord record = new SAMRecord(null);
		record.setReadName("TESTDATA");
		
		//first read
		record.setReadBases("ACCCT AACCC CAACC CTAAC CNTAA CCCTA ACCCA AC".replace(" ","" ).getBytes());
		record.setFlags(67); 	//first of pair forward
		record.setAttribute("MD", "A25A");  //(ref) A>C (base) in cycly 11 and 37
		record.setAttribute("RG", "first");
		record.setCigarString("10S27M");		
		report.parseTAGs(record);
		
		//second read
		record.setReadBases("ACCCT AACCC".replace(" ","" ).getBytes());
		record.setCigarString( "10M" );
		record.setAttribute( "MD", "T9" );  //ref T>A (Base) in cycle 1
		report.parseTAGs(record);
		
		//third read
		record.setReadNegativeStrandFlag(true);  //ref A>T (base) in cycle 10 according to reverse
		report.parseTAGs(record);
		
		//forth read invalid cigar so mutation and MD are ignored
		record.setReadBases("ACCCT AACCC A".replace(" ","" ).getBytes());
		record.setAttribute("RG", "last");
		report.parseTAGs(record);
		
		Element root = XmlElementUtils.createRootElement( XmlUtils.tag, null );
		report.toXml( root );		
		checkXml( root );
	}	
		
	private List<Element> getChildNameIs(Element parent, String eleName, String nameValue){		
		return XmlElementUtils.getChildElementByTagName(parent,eleName).stream().
			filter( e -> e.getAttribute( XmlUtils.Sname ).equals( nameValue ) ).collect(Collectors.toList());		
	}
	
	private void checkXml(Element root){
		 		
		assertEquals( 2, XmlElementUtils.getChildElementByTagName( root, XmlUtils.metricsEle ).size()  );				
		
		//<sequenceMetrics name="tags:MD:Z">
		Element metricE = getChildNameIs( root, XmlUtils.metricsEle, "tags:MD:Z" ).get(0);
		assertEquals( metricE.getChildNodes().getLength() , 3 );
		
		//check mutation on each base cycle
		Element ele = getChildNameIs( metricE, XmlUtils.variableGroupEle, XmlUtils.FirstOfPair ).get(0);
		//three of firstOfPair have four mutation base
		String[] values = new String[] { "A", "T", "C", "C" };
		String[] counts =  new String[] { "1", "10", "11", "37" };
		for(int i = 0; i < counts.length; i++ ) {
			
			String count = counts[i];
			Element vE = XmlElementUtils.getChildElementByTagName(ele, XmlUtils.baseCycleEle).stream().
				filter( e -> e.getAttribute( XmlUtils.Scycle ).equals( String.valueOf(count))  ).findFirst().get();		
			assertEquals( vE.getChildNodes().getLength() , 1 );
			vE = (Element) vE.getChildNodes().item(0);
			assertEquals( vE.getAttribute(XmlUtils.Svalue), values[i]);
			assertEquals( vE.getAttribute(XmlUtils.Scount), "1");
		}
		
		//check mutaiton type on forward reads
		ele = getChildNameIs(metricE, XmlUtils.variableGroupEle, XmlUtils.FirstOfPair+"ForwardStrand" ).get(0);
		assertEquals( 1, XmlElementUtils.getOffspringElementByTagName(ele, XmlUtils.Stally).stream()
			.filter(e -> e.getAttribute(XmlUtils.Svalue).equals("A>C") && e.getAttribute(XmlUtils.Scount).equals("2") ).count() );
		assertEquals( 1, XmlElementUtils.getOffspringElementByTagName(ele, XmlUtils.Stally).stream()
			.filter(e -> e.getAttribute(XmlUtils.Svalue).equals("T>A") && e.getAttribute(XmlUtils.Scount).equals("1") ).count() );		
		
		//check mutaiton type on reverse reads
		ele = getChildNameIs( metricE, XmlUtils.variableGroupEle, XmlUtils.FirstOfPair+"ReverseStrand" ).get(0);
		assertEquals( 1, XmlElementUtils.getOffspringElementByTagName(ele, XmlUtils.Stally).size());
		assertEquals( 1, XmlElementUtils.getOffspringElementByTagName(ele, XmlUtils.Stally).stream()
				.filter(e -> e.getAttribute(XmlUtils.Svalue).equals("A>T") && e.getAttribute(XmlUtils.Scount).equals("1") ).count() );
		
		//check tag RG
		ele = getChildNameIs( root, XmlUtils.metricsEle, "tags:RG:Z" ).get(0);
		assertEquals( 1, XmlElementUtils.getOffspringElementByTagName(ele, XmlUtils.Stally).stream()
				.filter(e -> e.getAttribute(XmlUtils.Svalue).equals("first") && e.getAttribute(XmlUtils.Scount).equals("3") ).count() );
		assertEquals( 1, XmlElementUtils.getOffspringElementByTagName(ele, XmlUtils.Stally).stream()
				.filter(e -> e.getAttribute(XmlUtils.Svalue).equals("last") && e.getAttribute(XmlUtils.Scount).equals("1") ).count() );
	}
		
	
	@Ignore
	public void testTagPosition() {
		final  SAMTagUtil STU = SAMTagUtil.getSingleton();
		final short CS = STU.CS;
		final short CQ = STU.CQ;
		final short RG = STU.RG;
		final short CM = STU.CM;
		final short SM = STU.SM;
		final short NH = STU.NH;
		final short MD = STU.MD;
		final short IH = STU.IH;
		// custom tags
		final short ZM = STU.makeBinaryTag("ZM");
		final short ZP = STU.makeBinaryTag("ZP");
		final short ZF = STU.makeBinaryTag("ZF");
		
		short [] tags = {CS, CQ, RG, ZM, ZP, CM, ZF, SM, IH, NH, MD};
		
		System.out.println("current");
		for (short tag : tags) 
			System.out.println(STU.makeStringTag(tag) + " : " + tag);
		
		System.out.println("ordered");
		short [] orderedTags = {MD, ZF, RG, IH, NH,CM,SM,ZM,ZP, CQ, CS};
		
		for (short tag : orderedTags) 
			System.out.println(STU.makeStringTag(tag) + " : " + tag);					
	}
	
	private void createMDerrFile() throws IOException{
		List<String> data = new ArrayList<String>();
        data.add("@HD	VN:1.0	SO:coordinate");
        data.add("@RG	ID:1959T	SM:eBeads_20091110_CD	DS:rl=50");
        data.add("@PG	ID:SOLID-GffToSam	VN:1.4.3");
        data.add("@SQ	SN:chr1	LN:249250621");
        data.add("@SQ	SN:chr11	LN:243199373");
	
	    data.add("HWI-ST1408:8	147	chrM	3085	255	28S96M2S	=	3021	-160	" + 
	    "CNNNNNNNNNNNNNNTNNANNNNNNNNTANNCNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNCNNAAGNACANGAGAAATAAGNCCTACTTCACAAAGCGCCTNCCCCCGNAAANGANN	" + 
	    "########################################################################F==#F==#EGGGGGGE<=#FE1GGGGGGGGGGGGGF=?#GGGE@=#E@?#<=##	" + 
	    "MD:Z:1T0C1A0G0G0T0C0G0G0T0T0T0C0T0A0T0C0T0A0C0N0T0T0C0A0A0A0T0T0C0C0T0C0C0C0T0G0T0A1G0A3G3A10G19T6T3T2	NH:i:1	HI:i:1	NM:i:47	AS:i:169	RG:Z:1959T");
	
		try(BufferedWriter out = new BufferedWriter(new FileWriter(INPUT_FILE))){	    
			for (String line : data)  out.write(line + "\n");	               
		}	
	}
	
	@Test
	public void tempTest() throws Exception{
		createMDerrFile();	
		Element root = XmlElementUtils.createRootElement("root",null);
		BamSummarizer2 bs = new BamSummarizer2();
		BamSummaryReport2 sr = (BamSummaryReport2) bs.summarize(INPUT_FILE); 
		sr.toXml(root);	 
	}	
	
}
