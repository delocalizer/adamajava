package au.edu.qimr.clinvar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.picard.fastq.FastqReader;
import net.sf.picard.fastq.FastqRecord;
import net.sf.samtools.util.SequenceUtil;
import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Serializer;

import org.qcmg.common.log.QLogger;
import org.qcmg.common.log.QLoggerFactory;
import org.qcmg.common.meta.QExec;
import org.qcmg.common.model.Accumulator;
import org.qcmg.common.model.ChrPosition;
import org.qcmg.common.string.StringUtils;
import org.qcmg.common.util.FileUtils;
import org.qcmg.common.util.LoadReferencedClasses;
import org.qcmg.common.util.Pair;
import org.qcmg.common.vcf.VcfRecord;
import org.qcmg.common.vcf.VcfUtils;
import org.qcmg.common.vcf.header.VcfHeader;
import org.qcmg.common.vcf.header.VcfHeader.Record;
import org.qcmg.common.vcf.header.VcfHeaderUtils;
import org.qcmg.qmule.SmithWatermanGotoh;
import org.qcmg.vcf.VCFFileWriter;

import au.edu.qimr.clinvar.model.Bin;
import au.edu.qimr.clinvar.model.FastqProbeMatch;
import au.edu.qimr.clinvar.model.IntPair;
import au.edu.qimr.clinvar.model.Probe;
import au.edu.qimr.clinvar.util.ClinVarUtil;
import au.edu.qimr.clinvar.util.FastqProbeMatchUtil;
//import org.w3c.dom.Document;
//import org.w3c.dom.Element;

public class Q3ClinVar {
	
private static QLogger logger;
	
	
	private static String[] fastqFiles;
	private static String version;
	private String logFile;
	private String xmlFile;
	private String outputFile;
	private int binId = 1;
	
	private int minBinSize = 10;
	
	private final Map<Integer, Map<String, Probe>> probeLengthMapR1 = new HashMap<>();
	private final Map<Integer, Map<String, Probe>> probeLengthMapR2 = new HashMap<>();
	
	private final Set<Probe> probeSet = new TreeSet<>();
	private final Map<Probe, AtomicInteger> probeDist = new HashMap<>();
	private final Map<Probe, List<Bin>> probeBinDist = new HashMap<>();
	
	private final Map<VcfRecord, List<Pair<Probe, Bin>>> mutations = new HashMap<>();
	private final Map<Probe, IntPair> probeReadCountMap = new HashMap<>();
	
	// used to check for uniqueness
	private final Set<String> probeSequences = new HashSet<>();
	
	private final Set<FastqProbeMatch> matches = new HashSet<>();
	
	
//	private final static Map<Pair<FastqRecord, FastqRecord>, List<Probe>> multiMatchingReads = new HashMap<>();
	
	private int exitStatus;
	
	protected int engage() throws Exception {
		
		try {
			Builder parser = new Builder();
			Document doc = parser.build(xmlFile);
			Element root = doc.getRootElement();
			
			// get probes from doc
			Element probes = root.getFirstChildElement("Probes");
			Elements probesElements = probes.getChildElements();
			
			int i = 0;
			for ( ; i < probesElements.size() ; i++) {
				Element probe = probesElements.get(i);
				int id = Integer.parseInt(probe.getAttribute("id").getValue());
			
				String dlsoSeq = "";
				String ulsoSeq = "";
				String dlsoSeqRC = "";
				String ulsoSeqRC = "";
				String subseq = "";
				String chr = "";
				boolean forwardStrand = false;
				int p1Start = -1;
				int p1End = -1;
				int p2Start = -1;
				int p2End = -1;
				int ssStart = -1;
				int ssEnd = -1;
//				int start = -1;
//				int end = -1;
				
				Elements probeElements = probe.getChildElements();
			
				for (int j = 0 ; j < probeElements.size() ; j++) {
					Element probeSubElement = probeElements.get(j);
 					if ("DLSO_Sequence".equals(probeSubElement.getQualifiedName())) {
 						dlsoSeq = probeSubElement.getValue();
 					} else  if ("ULSO_Sequence".equals(probeSubElement.getQualifiedName())) {
 						ulsoSeq = probeSubElement.getValue();
 					} else if ("dlsoRevCompSeq".equals(probeSubElement.getQualifiedName())) {
 						dlsoSeqRC = probeSubElement.getValue();
 					} else  if ("ulsoRevCompSeq".equals(probeSubElement.getQualifiedName())) {
 						ulsoSeqRC = probeSubElement.getValue();
 					} else  if ("primer1Start".equals(probeSubElement.getQualifiedName())) {
 						p1Start = Integer.parseInt(probeSubElement.getValue()) + 1;						// add 1 to make it 1-based
 					} else  if ("primer1End".equals(probeSubElement.getQualifiedName())) {
 						p1End = Integer.parseInt(probeSubElement.getValue()) + 1;						// add 1 to make it 1-based
 					} else  if ("primer2Start".equals(probeSubElement.getQualifiedName())) {
 						p2Start = Integer.parseInt(probeSubElement.getValue()) + 1;						// add 1 to make it 1-based
 					} else  if ("primer2End".equals(probeSubElement.getQualifiedName())) {
 						p2End = Integer.parseInt(probeSubElement.getValue()) + 1;						// add 1 to make it 1-based
 					} else  if ("subseq".equals(probeSubElement.getQualifiedName())) {
 						subseq = probeSubElement.getValue();
 					} else  if ("subseqStart".equals(probeSubElement.getQualifiedName())) {
 						ssStart =  Integer.parseInt(probeSubElement.getValue()) + 1;						// add 1 to make it 1-based
 					} else  if ("subseqEnd".equals(probeSubElement.getQualifiedName())) {
 						ssEnd =  Integer.parseInt(probeSubElement.getValue()) + 1;						// add 1 to make it 1-based
 					} else  if ("Chromosome".equals(probeSubElement.getQualifiedName())) {
 						chr =  probeSubElement.getValue();
 					} else  if ("Probe_Strand".equals(probeSubElement.getQualifiedName())) {
 						forwardStrand =  "+".equals(probeSubElement.getValue());
 					} 
// 					if ("Start_Position".equals(probeSubElement.getQualifiedName())) {
// 						start =  Integer.parseInt(probeSubElement.getValue());
// 					}
// 					if ("End_Position".equals(probeSubElement.getQualifiedName())) {
// 						end =  Integer.parseInt(probeSubElement.getValue());
// 					}
				}
				
				Probe p = new Probe(id, dlsoSeq, dlsoSeqRC, ulsoSeq, ulsoSeqRC, p1Start, p1End, p2Start, p2End, subseq, ssStart, ssEnd, chr, forwardStrand);
				probeSet.add(p);
				int primerLengthR1 = dlsoSeqRC.length();
				int primerLengthR2 = ulsoSeq.length();
				
				Map<String, Probe> mapR1 = probeLengthMapR1.get(primerLengthR1);
				if (null == mapR1) {
					mapR1 = new HashMap<>();
					probeLengthMapR1.put(primerLengthR1, mapR1);
				}
				mapR1.put(dlsoSeqRC, p);
				
				Map<String, Probe> mapR2 = probeLengthMapR2.get(primerLengthR2);
				if (null == mapR2) {
					mapR2 = new HashMap<>();
					probeLengthMapR2.put(primerLengthR2, mapR2);
				}
				mapR2.put(ulsoSeq, p);
				
				probeSequences.add(dlsoSeq);
				probeSequences.add(dlsoSeqRC);
				probeSequences.add(ulsoSeq);
				probeSequences.add(ulsoSeqRC);
				
			}
			
			logger.info("Found " + i + " probes in xml doc. No of entries in seq set is: " + probeSequences.size() + " which should be equals to : " + (4 * i));
			
			
			List<Integer> primerLengthsR1 = new ArrayList<>(probeLengthMapR1.keySet());
			Collections.sort(primerLengthsR1);
			Collections.reverse(primerLengthsR1);
			for (Integer xyz : primerLengthsR1) {
				logger.info("no of probes with primer length " + xyz.intValue() + ": " + probeLengthMapR1.get(xyz).size());
			}
			
			List<Integer> primerLengthsR2 = new ArrayList<>(probeLengthMapR2.keySet());
			Collections.sort(primerLengthsR2);
			Collections.reverse(primerLengthsR2);
			for (Integer xyz : primerLengthsR2) {
				logger.info("no of probes with primer length " + xyz.intValue() + ": " + probeLengthMapR2.get(xyz).size());
			}
			
			int fastqCount = 0;
			// read in a fastq file and lits see if we get some matches
			try (FastqReader reader1 = new FastqReader(new File(fastqFiles[0]));
					FastqReader reader2 = new FastqReader(new File(fastqFiles[1]));) {
				
				for (FastqRecord rec : reader1) {
					FastqRecord rec2 = reader2.next();
					
					FastqProbeMatch fpm = new FastqProbeMatch(fastqCount++, rec, rec2);
					matches.add(fpm);
				}
			}
			
			int matchCount = 0;
			int mateMatchCount = 0;
			
			for (FastqProbeMatch fpm : matches) {
				
				final String read1 = fpm.getRead1().getReadString();
				final String read2 = fpm.getRead2().getReadString();
					
				boolean secondReadMatchFound = false;
				// loop through the maps searching for a match
				for (Integer pl : primerLengthsR1) {
					String read =read1.substring(0, pl.intValue());
						
					Map<String, Probe> map = probeLengthMapR1.get(pl);
						
						if (map.containsKey(read)) {
							Probe p = map.get(read);
							matchCount++;
							// set read1 probe
							fpm.setRead1Probe(p, 0);
							
							// now check to see if mate starts with the value in the map
							String mate = p.getUlsoSeq();
							if (read2.startsWith(mate)) {
								secondReadMatchFound = true;
								
								// set read2 probe
								fpm.setRead2Probe(p, 0);
								mateMatchCount++;
								updateMap(p, probeDist);
							}
							// no need to look at other maps
							break;
						}
					}
					
					if ( ! secondReadMatchFound) {
						for (Integer pl : primerLengthsR2) {
							String read = read2.substring(0, pl.intValue());
							Map<String, Probe> map = probeLengthMapR2.get(pl);
							
							if (map.containsKey(read)) {
								Probe p = map.get(read);
								
								// set read2 probe
								fpm.setRead2Probe(p, 0);
								// no need to look at other maps
								break;
							}
						}
					}
			}
					
			logger.info("no of records in fastq file: " + fastqCount + ", and no of records that started with a probe in our set: " + matchCount + " and no whose mate also matched: " + mateMatchCount);
			
			FastqProbeMatchUtil.getStats(matches);
			
			//TODO - PUT THIS BACK IN
			logger.info("Rescue reads using edit distances");
			findNonExactMatches();
			FastqProbeMatchUtil.getStats(matches);
			
			setupFragments();
			binFragments();
			
			writeCsv();
			
			writeDiagnosticOutput();
			
//			writeOutput();
		} finally {
		}
		return exitStatus;
	}

	private void writeDiagnosticOutput() throws IOException {
		
		List<FastqProbeMatch> multiMatched = new ArrayList<>();
		List<FastqProbeMatch> oneEndMatched = new ArrayList<>();
		List<FastqProbeMatch> noEndsMatched = new ArrayList<>();
		List<FastqProbeMatch> noFragment = new ArrayList<>();
		
		for (FastqProbeMatch fpm : matches) {
			
			if (FastqProbeMatchUtil.isMultiMatched(fpm)) {
				multiMatched.add(fpm);
			} else if (FastqProbeMatchUtil.neitherReadsHaveAMatch(fpm)) {
				noEndsMatched.add(fpm);
			} else if (FastqProbeMatchUtil.onlyOneReadHasAMatch(fpm)) {
				oneEndMatched.add(fpm);
			} else if (StringUtils.isNullOrEmpty(fpm.getFragment())) {
				noFragment.add(fpm);
			}
		}
		
		logger.info("multiMatched: " + multiMatched.size());
		logger.info("oneEndMatched: " + oneEndMatched.size());
		logger.info("noEndsMatched: " + noEndsMatched.size());
		logger.info("noFragment: " + noFragment.size());
		
		
		if ( ! multiMatched.isEmpty()) {
			try (FileWriter writer = new FileWriter(new File(outputFile+".diagnostic.multi.matched.csv"))){;
				writer.write("r1_probe_id,r2_probe_id,r1,r2" );
				writer.write("\n");
				
				for (FastqProbeMatch fpm : multiMatched) {
					writer.write(fpm.getRead1Probe().getId() + "," + fpm.getRead2Probe().getId() + "," + fpm.getRead1().getReadString() + "," + fpm.getRead2().getReadString());
					writer.write("\n");
				}
				writer.flush();
			}
		}
		if ( ! noFragment.isEmpty()) {
			try (FileWriter writer = new FileWriter(new File(outputFile+".diagnostic.no.fragment.csv"))){;
			writer.write("r1_probe_id,r2_probe_id,r1,r2" );
			writer.write("\n");
			
			for (FastqProbeMatch fpm : noFragment) {
				writer.write(fpm.getRead1Probe().getId() + "," + fpm.getRead2Probe().getId() + "," + fpm.getRead1().getReadString() + "," + fpm.getRead2().getReadString());
				writer.write("\n");
			}
			writer.flush();
			}
		}
		if ( ! oneEndMatched.isEmpty()) {
			try (FileWriter writer = new FileWriter(new File(outputFile+".diagnostic.one.end.matched.csv"))){;
			writer.write("r1_probe_id,r2_probe_id,r1,r2" );
			writer.write("\n");
			
			for (FastqProbeMatch fpm : oneEndMatched) {
				String p1 = null != fpm.getRead1Probe() ? fpm.getRead1Probe().getId() + "" : "-";
				String p2 = null != fpm.getRead2Probe() ? fpm.getRead2Probe().getId() + "" : "-";
				writer.write(p1 + "," + p2 + "," + fpm.getRead1().getReadString() + "," + fpm.getRead2().getReadString());
				writer.write("\n");
			}
			writer.flush();
			}
		}
		if ( ! noEndsMatched.isEmpty()) {
			try (FileWriter writer = new FileWriter(new File(outputFile+".diagnostic.no.end.matched.csv"))){;
			writer.write("r1,r2" );
			writer.write("\n");
			
			for (FastqProbeMatch fpm : noEndsMatched) {
				writer.write(fpm.getRead1().getReadString() + "," + fpm.getRead2().getReadString());
				writer.write("\n");
			}
			writer.flush();
			}
		}
		
	
		
		
	}

	private void writeCsv() throws IOException {
		// TODO Auto-generated method stub
		List<VcfRecord> allVcfs = new ArrayList<>();
		for (Entry<Probe, List<Bin>> entry : probeBinDist.entrySet()) {
			Probe p = entry.getKey();
			List<Bin> bins = entry.getValue();
			String ref = p.getReferenceSequence();
			boolean forwardStrand = p.isOnForwardStrand();
			
			// get largest bin
			if (null != bins && ! bins.isEmpty()) {
				
				
				for (Bin b : bins) {
				
					// only care about bins that have more than 10 reads
					if (b.getRecordCount() >= minBinSize) {
					
						String binSeq = forwardStrand ? SequenceUtil.reverseComplement(b.getSequence()) : b.getSequence() ;
						
	//						logger.info("probe " + p.getId() + ", forwardStrand: " + forwardStrand + ", largest bin does not equal ref!! ref: " + ref + ", binSeq: " + binSeq);
							
							
						SmithWatermanGotoh nm = new SmithWatermanGotoh(ref, binSeq, 5, -4, 16, 4);
						String [] diffs = nm.traceback();
						b.setSWDiffs(diffs);
							
						if (p.getId() == 241) {
							logger.info("probe: " + p.getId() + ", forward strand: " + p.isOnForwardStrand() + ", size: " + b.getRecordCount() + ", ref: " + ref + ", binSeq: " + binSeq);
							for (String s : diffs) {
								logger.info(s);
							}
						}
						if ( ! binSeq.equals(ref)) {
//							if (ref.length() != binSeq.length() &&  ! diffs[0].contains("-") && ! diffs[2].contains("-")) {
//								logger.warn("ref length != binSeq and no indels!!! p id: " + p.getId() + ", bin id: " + b.getId() + ", fs: " + p.isOnForwardStrand());
//							}
							
							createMutations(p, b);
						}
					}
				}
			}
		}
		
		List<VcfRecord> sortedPositions = new ArrayList<>(mutations.keySet());
		Collections.sort(sortedPositions);
		
		try (FileWriter writer = new FileWriter(new File(outputFile+".mutations.csv"))) {
			writer.write("chr,position,ref,alt,probe_id,probe_total_reads,probe_total_frags,bin_id,bin_record_count" );
			writer.write("\n");
			
			
			for (VcfRecord vcf : sortedPositions) {
				List<Pair<Probe, Bin>> list = mutations.get(vcf);
				for (Pair<Probe, Bin> pair : list) {
					Probe p = pair.getLeft();
					Bin b = pair.getRight();
					// get some probe stats
					
					//loop through fmps and for this probe, get number that have frags, and number that don't
					IntPair ip = probeReadCountMap.get(p);
					int totalReads = ip.getInt1();
					int totalFrags = ip.getInt2();
//					for (FastqProbeMatch fpm : matches) {
//						if (FastqProbeMatchUtil.isProperlyMatched(fpm) && fpm.getRead1Probe().equals(p)) {
//							totalReads++;
//							if (null != fpm.getFragment()) {
//								totalFrags++;
//							}
//						}
//					}
					
					writer.write(vcf.getChromosome() + "," + vcf.getPosition() + "," + vcf.getRef() + "," + vcf.getAlt() + "," + p.getId() + "," + totalReads + "," + totalFrags + "," + b.getId() + "," + b.getRecordCount());
					writer.write("\n");
				}
			}
			writer.flush();
		}
		
		
		logger.info("pre-rollup no of mutataions: " + mutations.size());
		
		rollupMutations();
		
		logger.info("post-rollup no of mutataions: " + mutations.size());
		
		sortedPositions = new ArrayList<>(mutations.keySet());
		Collections.sort(sortedPositions);
		logger.info("no of vcf positions for vcf file: " + sortedPositions.size());
		
		try (VCFFileWriter writer = new VCFFileWriter(new File(outputFile+".mutations.vcf"))) {
			
			/*
			 * Setup the VcfHeader
			 */
			final DateFormat df = new SimpleDateFormat("yyyyMMdd");
			VcfHeader header = new VcfHeader();
			header.parseHeaderLine(VcfHeaderUtils.CURRENT_FILE_VERSION);		
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_FILE_DATE + "=" + df.format(Calendar.getInstance().getTime()));		
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_UUID_LINE + "=" + QExec.createUUid());		
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_SOURCE_LINE + "=q3ClinVar");		
			header.addFormatLine("BB", ".","String","Breakdown of Bins containing more than 1 read at this position in the following format: Base,NumberOfReadsSupportingBase,NumberOfAmplicons/NumberOfBins.... "
					+ "NOTE that only bins with number of reads greater than " + minBinSize + " will be shown here");
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_FINAL_HEADER_LINE_INCLUDING_FORMAT);
			
			Iterator<Record> iter = header.iterator();
			while (iter.hasNext()) {
				writer.addHeader(iter.next().toString() );
			}
			
			for (VcfRecord vcf : sortedPositions) {
				
				
				/*
				 * get amplicons that overlap this position
				 */
				List<Probe> overlappingProbes = ClinVarUtil.getAmpliconsOverlappingPosition(vcf.getChrPosition(), probeSet);
//				logger.info("no of amplicons overlapping position: " + overlappingProbes.size());
				
				if (overlappingProbes.isEmpty()) {
					logger.warn("Found no amplicons overlapping position: " + vcf.getChrPosition());
				}
				String format = ClinVarUtil.getAmpliconDistribution(vcf, overlappingProbes, probeBinDist, minBinSize);
				List<String> ff = new ArrayList<>();
				ff.add("BB");
				ff.add(ClinVarUtil.getSortedBBString(format, vcf.getRef()));
				vcf.setFormatFields(ff);
				
				writer.add(vcf);
			}
		}
		try (VCFFileWriter writer = new VCFFileWriter(new File(outputFile+".diagnostic.mutations.vcf"))) {
			
			/*
			 * Setup the VcfHeader
			 */
			final DateFormat df = new SimpleDateFormat("yyyyMMdd");
			VcfHeader header = new VcfHeader();
			header.parseHeaderLine(VcfHeaderUtils.CURRENT_FILE_VERSION);		
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_FILE_DATE + "=" + df.format(Calendar.getInstance().getTime()));		
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_UUID_LINE + "=" + QExec.createUUid());		
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_SOURCE_LINE + "=q3ClinVar");		
			header.addFormatLine("BB", ".","String","Breakdown of Bins containing more than 1 read at this position in the following format: Base,NumberOfReadsSupportingBase,AmpliconID/BinID(# of reads in bin),AmpliconID/BinID(# of reads in bin).... "
					+ "NOTE that only bins with number of reads greater than " + minBinSize + " will be shown here");
			header.parseHeaderLine(VcfHeaderUtils.STANDARD_FINAL_HEADER_LINE_INCLUDING_FORMAT);
			
			Iterator<Record> iter = header.iterator();
			while (iter.hasNext()) {
				writer.addHeader(iter.next().toString() );
			}
			
			for (VcfRecord vcf : sortedPositions) {
				
				
				/*
				 * get amplicons that overlap this position
				 */
				List<Probe> overlappingProbes = ClinVarUtil.getAmpliconsOverlappingPosition(vcf.getChrPosition(), probeSet);
//				logger.info("no of amplicons overlapping position: " + overlappingProbes.size());
				
				if (overlappingProbes.isEmpty()) {
					logger.warn("Found no amplicons overlapping position: " + vcf.getChrPosition());
				}
				String format = ClinVarUtil.getAmpliconDistribution(vcf, overlappingProbes, probeBinDist, minBinSize, true);
				List<String> ff = new ArrayList<>();
				ff.add("BB");
				ff.add(ClinVarUtil.getSortedBBString(format, vcf.getRef()));
				vcf.setFormatFields(ff);
				
				writer.add(vcf);
			}
		}
	}
	
	
	private void rollupMutations() {
		
		Map<ChrPosition, Set<VcfRecord>> potentialRollups = new HashMap<>();
		for (VcfRecord vcf : mutations.keySet()) {
			/*
			 * check to see if there are other mutations in this collection that are at the same position
			 */
			for (VcfRecord vcf2 : mutations.keySet()) {
				if (vcf != vcf2) {
					if (vcf.getChrPosition().getChromosome().equals(vcf2.getChrPosition().getChromosome()) && vcf.getChrPosition().getPosition() == vcf2.getChrPosition().getPosition()) {
						
						ChrPosition cp = new ChrPosition(vcf.getChrPosition().getChromosome(), vcf.getChrPosition().getPosition());
						Set<VcfRecord> records = potentialRollups.get(cp);
						if (null == records) {
							records = new HashSet<>();
							potentialRollups.put(cp, records);
						}
						records.add(vcf);
						records.add(vcf2);
					}
				}
			}
		}
		logger.info("no of positions in potentialRollups: " + potentialRollups.size());
		
		
		for (Entry<ChrPosition, Set<VcfRecord>> entry : potentialRollups.entrySet()) {
			for (VcfRecord vcf: entry.getValue()) {
				logger.info("vcf: " + vcf.toString());
			}
		}
		
		/*
		 * Rollup
		 */
		for (Entry<ChrPosition, Set<VcfRecord>> entry : potentialRollups.entrySet()) {
			VcfRecord mergedRecord = VcfUtils.mergeVcfRecords(entry.getValue());
			
			for (VcfRecord vcf: entry.getValue()) {
				mutations.remove(vcf);
			}
			mutations.put(mergedRecord,null);
		}
		
	}

	private void createMutations(Probe p, Bin b) {
		
		String [] smithWatermanDiffs = b.getSmithWatermanDiffs();
		List<Pair<Integer, String>> mutations = ClinVarUtil.getPositionRefAndAltFromSW(smithWatermanDiffs);
		
		if ( ! mutations.isEmpty()) {
		
			String probeRef = p.getReferenceSequence();
			// remove any indel characters - only checking
			String swRef = smithWatermanDiffs[0].replace("-", "");
			int offset = probeRef.indexOf(swRef);
			
			if ( offset == -1) {
				logger.warn("probeRef.indexOf(swRef) == -1!!! probe (id:bin.id: " + p.getId() + ":" + b.getId() + ") , probe ref: " + probeRef + ", swRef: " + swRef);
			}
			
			for (Pair<Integer, String> mutation : mutations) {
				int position = mutation.getLeft().intValue();
				String mutString = mutation.getRight();
				int slashIndex = mutString.indexOf('/');
				String ref = mutString.substring(0, slashIndex);
				String alt = mutString.substring(slashIndex + 1);
	//			if (p.getId() == 542 && b.getId() == 87424 ) {
	//				logger.info("position: " + position + ", mutString: " + mutString);
	//			}
				createMutation(p, b, position + offset, ref, alt);
			}
		}
	}
	
	private void createMutation(Probe p, Bin b, int position, String ref, String alt) {
		int startPos = p.getCp().getPosition() + position;
		int endPos = ref.length() > 1 ? startPos + (ref.length() -1 ) : startPos;
		VcfRecord vcf = VcfUtils.createVcfRecord(new ChrPosition(p.getCp().getChromosome(),  startPos, endPos), "."/*id*/, ref, alt);
		List<Pair<Probe, Bin>> existingBins = mutations.get(vcf);
		if (null == existingBins) {
			existingBins = new ArrayList<>();
			mutations.put(vcf, existingBins);
		}
		existingBins.add(new Pair<Probe,Bin>(p,b));
	}
	
	public static String getAlleleCountsFromBins(List<Bin> bins, Bin originatingBin, int vcfPos, int positionInOrigBin) {
		Accumulator acc = new Accumulator(vcfPos);
		
		for (int i = 0; i < originatingBin.getRecordCount() ; i++) {
			acc.addBase((byte)originatingBin.getSequence().charAt(positionInOrigBin), (byte)33, true, vcfPos - positionInOrigBin , vcfPos, vcfPos - positionInOrigBin +originatingBin.getSequence().length() , 1);
		}
		
		
		return acc.getPileupElementString();
	}

	private void binFragments() {
	int noOfProbesWithLargeSecondaryBin = 0;
		
		Map<Probe, List<FastqProbeMatch>> probeDist = new HashMap<>();
		Map<Probe, Map<String, AtomicInteger>> probeFragments = new HashMap<>();
		Map<Probe, Map<IntPair, AtomicInteger>> probeMatchMap = new HashMap<>();
		
		for (FastqProbeMatch fpm : matches) {
			if (FastqProbeMatchUtil.isProperlyMatched(fpm)) {
				
				Probe p = fpm.getRead1Probe();
				
				FastqProbeMatchUtil.createFragment(fpm);
				List<FastqProbeMatch> fpms = probeDist.get(p);
				if (null == fpms) {
					fpms = new ArrayList<>();
					probeDist.put(p, fpms);
				}
				fpms.add(fpm);
				
				
				Map<String, AtomicInteger> probeFragment = probeFragments.get(p);
				if (null == probeFragment) {
					probeFragment = new HashMap<>();
					probeFragments.put(p, probeFragment);
				}
				updateMap(fpm.getFragment(), probeFragment);
				
				// get match scores
				Map<IntPair, AtomicInteger> matchMap = probeMatchMap.get(p);
				if (null == matchMap) {
					matchMap = new HashMap<>();
					probeMatchMap.put(p, matchMap);
				}
				updateMap(fpm.getScore(), matchMap);
			}
		}
		
		
		// logging and writing to file
		Element amplicons = new Element("Amplicons");
		amplicons.addAttribute(new Attribute("amplicon_file", xmlFile));
		amplicons.addAttribute(new Attribute("fastq_file_1", fastqFiles[0]));
		amplicons.addAttribute(new Attribute("fastq_file_2", fastqFiles[1]));
		
		for (Probe p : probeSet) {
			
			Element amplicon = new Element("Amplicon");
			amplicons.appendChild(amplicon);
			
			// attributes
			amplicon.addAttribute(new Attribute("probe_id", "" + p.getId()));
			amplicon.addAttribute(new Attribute("fragment_length", "" + p.getExpectedFragmentLength()));
			
			List<FastqProbeMatch> fpms = probeDist.get(p);
			
			Element fragments = new Element("Fragments");
			if (null != fpms && ! fpms.isEmpty()) {
				amplicon.appendChild(fragments);
				
				int fragmentExists = 0;
				for (FastqProbeMatch fpm : fpms) {
					if (FastqProbeMatchUtil.doesFPMMatchProbe(fpm, p)) {
						
						String frag = fpm.getFragment();
						if (null != frag) {
							fragmentExists++;
						}
					}
				}
				// add some attributtes
				fragments.addAttribute(new Attribute("total_number_of_reads", "" + fpms.size()));
				fragments.addAttribute(new Attribute("reads_containing_fragments", "" + fragmentExists));
				Map<IntPair, AtomicInteger> matchMap = probeMatchMap.get(p);
				if (null != matchMap) {
					StringBuilder sb = new StringBuilder();
					for (Entry<IntPair, AtomicInteger> entry : matchMap.entrySet()) {
						sb.append(entry.getKey().getInt1()).append("/").append(entry.getKey().getInt2());
						sb.append(":").append(entry.getValue().get()).append(',');
					}
					fragments.addAttribute(new Attribute("probe_match_breakdown", sb.toString()));
				}
			}
			
			// fragments next
			Map<String, AtomicInteger> frags = probeFragments.get(p);
			if (null != frags && ! frags.isEmpty()) {
				
				// I want the old fragment breakdown so that a comparison can be made
				List<Integer> fragMatches = new ArrayList<>();
				for (Entry<String, AtomicInteger> entry : frags.entrySet()) {
					if (entry.getKey() != null) {
						fragMatches.add(entry.getValue().get());
					}
				}
				if (fragMatches.size() > 1) {
					Collections.sort(fragMatches);
					Collections.reverse(fragMatches);
					
					fragments.addAttribute(new Attribute("old_fragment_breakdown", "" + ClinVarUtil.breakdownEditDistanceDistribution(fragMatches)));
				}
				
				List<Bin> bins = convertFragmentsToBins(frags);
				Collections.sort(bins);
				// keep this for vcf building
				probeBinDist.put(p, bins);
				
				//reset
				fragMatches = new ArrayList<>();
				for (Bin b : bins) {
						Element fragment = new Element("Fragment");
						fragments.appendChild(fragment);
						fragment.addAttribute(new Attribute("fragment_length", "" + b.getLength()));
						fragment.addAttribute(new Attribute("record_count", "" + b.getRecordCount()));
						fragment.addAttribute(new Attribute("diffs", "" + b.getDifferences()));
						fragment.addAttribute(new Attribute("id", "" + b.getId()));
						fragment.appendChild(b.getSequence());
						fragMatches.add(b.getRecordCount());
				}
				if (fragMatches.size() > 1) {
					Collections.sort(fragMatches);
					Collections.reverse(fragMatches);
					
					fragments.addAttribute(new Attribute("fragment_breakdown", "" + ClinVarUtil.breakdownEditDistanceDistribution(fragMatches)));
					
					int total = 0;
					for (Integer i : fragMatches) {
						total += i;
					}
					int secondLargestCount = fragMatches.get(1);
					if (secondLargestCount > 1) {
						int secondLargestPercentage = (100 * secondLargestCount) / total;
						if (secondLargestPercentage > 10) {
							noOfProbesWithLargeSecondaryBin++;
						}
					}
				}
			}
		}
		logger.info("no of probes with secondary bin contains > 10% of reads: " + noOfProbesWithLargeSecondaryBin);
		
		
		// write output
		Document doc = new Document(amplicons);
		File binnedOutput = new File(outputFile.replace(".xml", "_binned.xml"));
		try (OutputStream os = new FileOutputStream(binnedOutput);){
			 Serializer serializer = new Serializer(os, "ISO-8859-1");
		        serializer.setIndent(4);
		        serializer.setMaxLength(64);
		        serializer.write(doc);  
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private List<Bin> convertFragmentsToBins(Map<String, AtomicInteger> frags) {
		List<Bin> singleBins = new ArrayList<>();
		List<Bin> multipleBins = new ArrayList<>();
		
		for (Entry<String, AtomicInteger> entry : frags.entrySet()) {
			int tally = entry.getValue().get();
			String sequence = entry.getKey();
			if (null != sequence) {
				Bin b = new Bin(binId++, sequence, tally);
				if (tally > 1) {
					multipleBins.add(b);
				} else {
					singleBins.add(b);
				}
			}
		}
		
		// sort multiple bins so that the largest is first
		Collections.sort(multipleBins);
		
		// loop through single bins and see if we can assign to a bigger bin
		Iterator<Bin> iter = singleBins.iterator();
		while(iter.hasNext()) {
			Bin sb = iter.next();
			// loop through multi bins
			for (Bin mb : multipleBins) {
				// nned to have the same length
				if (sb.getLength() == mb.getLength()) {
					// need to have a BED of 1
					int basicEditDistance = ClinVarUtil.getBasicEditDistance(sb.getSequence(), mb.getSequence());
					if (basicEditDistance == 1) {
						
						// roll sb into mb
						mb.addSequence(sb.getSequence());
						
						// remove this single bin from singleBins
						iter.remove();
						
						// no need to look at other multi bins
						break;
					}
				}
			}
		}
		
		//combine and return
		multipleBins.addAll(singleBins);
		return multipleBins;
	}

	private void writeOutput() {
		Element amplicons = new Element("Amplicons");
		
//		List<Probe> orderedProbes = new ArrayList<>(probeSet);
//		Collections.sort(orderedProbes);
		
		for (Probe p : probeSet) {
			Element amplicon = new Element("Amplicon");
			amplicons.appendChild(amplicon);
			
			// attributes
			amplicon.addAttribute(new Attribute("probe_id", "" + p.getId()));
			amplicon.addAttribute(new Attribute("fragment_length", "" + p.getExpectedFragmentLength()));
			
			// fragments
			int perfectMatch = 0;
			int totalCount = 0;
			int fragmentExists = 0;
			Map<String, AtomicInteger> fragmentMap = new HashMap<>();
			for (FastqProbeMatch fpm : matches) {
				if (FastqProbeMatchUtil.doesFPMMatchProbe(fpm, p)) {
					totalCount++;
					if (FastqProbeMatchUtil.isProperlyMatched(fpm)) {
						perfectMatch++;
					}
					
					String frag = fpm.getFragment();
					if (null != frag) {
						fragmentExists++;
						updateMap(frag, fragmentMap);
					}
				}
			}
			
			Element fragments = new Element("Fragments");
			amplicon.appendChild(fragments);
			
			// attributes for the fragments element
			fragments.addAttribute(new Attribute("total_count", "" + totalCount));
			fragments.addAttribute(new Attribute("perfect_probe_match", "" + perfectMatch));
			fragments.addAttribute(new Attribute("fragment_count", "" + fragmentExists));
			
			for (Entry<String, AtomicInteger> entry : fragmentMap.entrySet()) {
				Element fragment = new Element("Fragment");
				fragments.appendChild(fragment);
				
				fragment.addAttribute(new Attribute("fragment_length", "" + entry.getKey().length()));
				fragment.addAttribute(new Attribute("record_count", "" + entry.getValue().get()));
				fragment.appendChild(entry.getKey());
			}
		}
		
		Document doc = new Document(amplicons);
		try (OutputStream os = new FileOutputStream(new File(outputFile));){
			 Serializer serializer = new Serializer(os, "ISO-8859-1");
		        serializer.setIndent(4);
		        serializer.setMaxLength(64);
		        serializer.write(doc);  
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private void findNonExactMatches() {
		int maxSingleEditDistance = 2;
		for (FastqProbeMatch fpm : matches) {
			if (FastqProbeMatchUtil.neitherReadsHaveAMatch(fpm)) {
				// both reads missing probes
				String read1 = fpm.getRead1().getReadString();
				String read2 = fpm.getRead2().getReadString();
				
				for (Probe p : probeSet) {
					String dlsoRC = p.getDlsoSeqRC();
					String ulso = p.getUlsoSeq();
					
					
					int [] editDistances = ClinVarUtil.getDoubleEditDistance(read1, read2, dlsoRC, ulso, maxSingleEditDistance);
					if (editDistances[0] <= maxSingleEditDistance 
							&& editDistances[1] <= maxSingleEditDistance) {
						
						// it could be the case that we have already found a matching probe, so make sure this match is better!!!
						
						if (FastqProbeMatchUtil.neitherReadsHaveAMatch(fpm)) {
							fpm.setRead1Probe(p, editDistances[0]);
							fpm.setRead2Probe(p, editDistances[1]);
						} else {
							// already have some probes set...
							logger.warn("have to determine which match is best...");
						}
					} else if (editDistances[0] <= maxSingleEditDistance) {
						// it could be the case that we have already found a matching probe, so make sure this match is better!!!
						
						if (FastqProbeMatchUtil.neitherReadsHaveAMatch(fpm)) {
							fpm.setRead1Probe(p, editDistances[0]);
						} else {
							// already have some probes set...
							logger.warn("have to determine which match is best...");
						}
						
					} else if (editDistances[1] <= maxSingleEditDistance) {
						// it could be the case that we have already found a matching probe, so make sure this match is better!!!
						
						if (FastqProbeMatchUtil.neitherReadsHaveAMatch(fpm)) {
							fpm.setRead2Probe(p, editDistances[1]);
						} else {
							// already have some probes set...
							logger.warn("have to determine which match is best...");
						}
						
					}
				}
				
			} else if ( ! FastqProbeMatchUtil.bothReadsHaveAMatch(fpm)) {
				// just 1 is missing a probe - find out which one, and try and
				String read = null;
				Probe existingProbe = null;
				boolean updateFirstReadProbe = null == fpm.getRead1Probe();
				
				if (updateFirstReadProbe) {
					read = fpm.getRead1().getReadString();
					existingProbe = fpm.getRead2Probe();
				} else {
					read = fpm.getRead2().getReadString();
					existingProbe = fpm.getRead1Probe();
				}
				
				// lets see if we are close to the existing probe
				int editDistance = ClinVarUtil.getEditDistance(read,  existingProbe.getDlsoSeqRC(), maxSingleEditDistance + 1);
				if (editDistance <= maxSingleEditDistance) {
					if (updateFirstReadProbe) {
						fpm.setRead1Probe(existingProbe, editDistance);
					} else {
						fpm.setRead2Probe(existingProbe, editDistance);
					}
				} else {
					// loop through the probes
					for (Probe p : probeSet) {
						String primer = updateFirstReadProbe ? p.getDlsoSeqRC() :  p.getUlsoSeq();
						editDistance = ClinVarUtil.getEditDistance(read, primer, maxSingleEditDistance + 1);
						
						if (editDistance <= maxSingleEditDistance) {
							// it could be the case that we have already found a matching probe, so make sure this match is better!!!
							
							if (updateFirstReadProbe && null == fpm.getRead1Probe()) {
								fpm.setRead1Probe(p, editDistance);
							} else if ( ! updateFirstReadProbe && null == fpm.getRead2Probe()) {
								fpm.setRead2Probe(p, editDistance);
							} else {
								// already have some probes set...
								logger.warn("have to determine which match is best...");
							}
						}
					}						
				}
			}
		}
	}
	
	public void setupFragments() {
		
		int noOfProbesWithLargeSecondaryBin = 0;
		
		Map<Probe, List<FastqProbeMatch>> probeDist = new HashMap<>();
		Map<Probe, Map<String, AtomicInteger>> probeFragments = new HashMap<>();
		Map<Probe, Map<IntPair, AtomicInteger>> probeMatchMap = new HashMap<>();
		
		for (FastqProbeMatch fpm : matches) {
			if (FastqProbeMatchUtil.isProperlyMatched(fpm)) {
				
				Probe p = fpm.getRead1Probe();
				
				FastqProbeMatchUtil.createFragment(fpm);
				List<FastqProbeMatch> fpms = probeDist.get(p);
				if (null == fpms) {
					fpms = new ArrayList<>();
					probeDist.put(p, fpms);
				}
				fpms.add(fpm);
				
				
				Map<String, AtomicInteger> probeFragment = probeFragments.get(p);
				if (null == probeFragment) {
					probeFragment = new HashMap<>();
					probeFragments.put(p, probeFragment);
				}
				updateMap(fpm.getFragment(), probeFragment);
				
				// get match scores
				Map<IntPair, AtomicInteger> matchMap = probeMatchMap.get(p);
				if (null == matchMap) {
					matchMap = new HashMap<>();
					probeMatchMap.put(p, matchMap);
				}
				updateMap(fpm.getScore(), matchMap);
			}
		}
		
		
		// logging and writing to file
		Element amplicons = new Element("Amplicons");
		amplicons.addAttribute(new Attribute("amplicon_file", xmlFile));
		amplicons.addAttribute(new Attribute("fastq_file_1", fastqFiles[0]));
		amplicons.addAttribute(new Attribute("fastq_file_2", fastqFiles[1]));
		
//		List<Probe> sortedProbes = new ArrayList<>(probeDist.keySet());
//		Collections.sort(sortedProbes);		// sorts on id of probe
		
		for (Probe p : probeSet) {
			
			Element amplicon = new Element("Amplicon");
			amplicons.appendChild(amplicon);
			
			// attributes
			amplicon.addAttribute(new Attribute("probe_id", "" + p.getId()));
			amplicon.addAttribute(new Attribute("fragment_length", "" + p.getExpectedFragmentLength()));
			
			List<FastqProbeMatch> fpms = probeDist.get(p);
			
			Element fragments = new Element("Fragments");
			if (null != fpms && ! fpms.isEmpty()) {
				amplicon.appendChild(fragments);
				
				int perfectMatch = 0;
				int fragmentExists = 0;
				for (FastqProbeMatch fpm : fpms) {
					if (FastqProbeMatchUtil.doesFPMMatchProbe(fpm, p)) {
						if (FastqProbeMatchUtil.isProperlyMatched(fpm)) {
							perfectMatch++;
						}
						
						String frag = fpm.getFragment();
						if (null != frag) {
							fragmentExists++;
						}
					}
				}
				
				// check ratio of fragments and total reads
				// if less than 10%, log...
				if ((100 * fragmentExists / fpms.size()) <= 10) {
					logger.info("fewer than 10% of reads made it to fragments for probe: " + p.getId() + " frag count: " + fragmentExists + ", total read count: " + fpms.size() + ", expected frag length: " + p.getExpectedFragmentLength());
						Set<Pair<String, String>> r1Set = new HashSet<>();
						for (FastqProbeMatch fpm : fpms) {
							if (null == fpm.getFragment()) {
								r1Set.add(new Pair<String, String>(fpm.getRead1().getReadString(), fpm.getRead2().getReadString()));
//								if (p.getId() == 48) {
//											logger.info("p: " + p.getId() + ", exp overlap: " + fpm.getExpectedReadOverlapLength() + ", r1: " + fpm.getRead1().getReadString() + ", r2: " + fpm.getRead2().getReadString());
//								}
							}
						}
						logger.info("no of unique r1,r2 pairs: " + r1Set.size());
				}
				
				
				// add some attributtes
				probeReadCountMap.put(p, new IntPair(fpms.size(), fragmentExists));
				fragments.addAttribute(new Attribute("total_number_of_reads", "" + fpms.size()));
				fragments.addAttribute(new Attribute("reads_containing_fragments", "" + fragmentExists));
//				fragments.addAttribute(new Attribute("perfect_probe_match", "" + perfectMatch));
				Map<IntPair, AtomicInteger> matchMap = probeMatchMap.get(p);
				if (null != matchMap) {
					StringBuilder sb = new StringBuilder();
					for (Entry<IntPair, AtomicInteger> entry : matchMap.entrySet()) {
						sb.append(entry.getKey().getInt1()).append("/").append(entry.getKey().getInt2());
						sb.append(":").append(entry.getValue().get()).append(',');
					}
					fragments.addAttribute(new Attribute("probe_match_breakdown", sb.toString()));
				}
			}
			
			// fragments next
			Map<String, AtomicInteger> frags = probeFragments.get(p);
			if (null != frags && ! frags.isEmpty()) {
				
				
				List<Integer> fragMatches = new ArrayList<>();
				for (Entry<String, AtomicInteger> entry : frags.entrySet()) {
					if (entry.getKey() != null) {
						Element fragment = new Element("Fragment");
						fragments.appendChild(fragment);
						fragment.addAttribute(new Attribute("fragment_length", "" + (entry.getKey().startsWith("+++") || entry.getKey().startsWith("---") ? entry.getKey().length() - 3 : entry.getKey().length())));
						fragment.addAttribute(new Attribute("record_count", "" + entry.getValue().get()));
						fragment.appendChild(entry.getKey());
//						logger.info("frag: " + entry.getKey() + ", count: " + entry.getValue().get());
						fragMatches.add(entry.getValue().get());
					}
				}
				if (fragMatches.size() > 1) {
					Collections.sort(fragMatches);
					Collections.reverse(fragMatches);
					
					fragments.addAttribute(new Attribute("fragment_breakdown", "" + ClinVarUtil.breakdownEditDistanceDistribution(fragMatches)));
					
					
					int total = 0;
					for (Integer i : fragMatches) {
						total += i;
					}
					int secondLargestCount = fragMatches.get(1);
					if (secondLargestCount > 1) {
						int secondLargestPercentage = (100 * secondLargestCount) / total;
						if (secondLargestPercentage > 10) {
							
							for (Entry<String, AtomicInteger> entry : frags.entrySet()) {
								if (entry.getKey() != null) {
//									logger.info("frag: " + entry.getKey() + ", count: " + entry.getValue().get());
//									fragMatches.add(entry.getValue().get());
								}
							}
							
							noOfProbesWithLargeSecondaryBin++;
//							logger.info("secondLargestPercentage is greater than 10%!!!: " + secondLargestPercentage);
//							logger.info("fragMatches: " + ClinVarUtil.breakdownEditDistanceDistribution(fragMatches));
						}
					}
				}
			}
			
		}
		logger.info("no of probes with secondary bin contains > 10% of reads: " + noOfProbesWithLargeSecondaryBin);
		
		// write output
		Document doc = new Document(amplicons);
		try (OutputStream os = new FileOutputStream(new File(outputFile));){
			 Serializer serializer = new Serializer(os, "ISO-8859-1");
		        serializer.setIndent(4);
		        serializer.setMaxLength(64);
		        serializer.write(doc);  
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static final <P> void updateMap(P p, Map<P, AtomicInteger> map) {
		AtomicInteger ai = map.get(p);
		if (null == ai) {
			ai = new AtomicInteger(1);
			map.put(p,  ai);
		} else {
			ai.incrementAndGet();
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		// loads all classes in referenced jars into memory to avoid nightly build sheninegans
		LoadReferencedClasses.loadClasses(Q3ClinVar.class);
		
		Q3ClinVar qp = new Q3ClinVar();
		int exitStatus = qp.setup(args);
		if (null != logger) {
			logger.logFinalExecutionStats(exitStatus);
		} else {
			System.err.println("Exit status: " + exitStatus);
		}
		
		System.exit(exitStatus);
	}
	
	protected int setup(String args[]) throws Exception{
		int returnStatus = 1;
		Options options = new Options(args);

		if (options.hasHelpOption()) {
			System.err.println(Messages.USAGE);
			options.displayHelp();
			returnStatus = 0;
		} else if (options.hasVersionOption()) {
			System.err.println(Messages.getVersionMessage());
			returnStatus = 0;
		} else if (options.getFastqs().length < 1) {
			System.err.println(Messages.USAGE);
//		} else if ( ! options.hasLogOption()) {
//			System.err.println(Messages.USAGE);
		} else {
			// configure logging
			logFile = options.getLog();
			version = Q3ClinVar.class.getPackage().getImplementationVersion();
			logger = QLoggerFactory.getLogger(Q3ClinVar.class, logFile, options.getLogLevel());
			logger.logInitialExecutionStats("q3clinvar", version, args);
			
			// get list of file names
			fastqFiles = options.getFastqs();
			if (fastqFiles.length < 1) {
				throw new Exception("INSUFFICIENT_ARGUMENTS");
			} else {
				// loop through supplied files - check they can be read
				for (int i = 0 ; i < fastqFiles.length ; i++ ) {
					if ( ! FileUtils.canFileBeRead(fastqFiles[i])) {
						throw new Exception("INPUT_FILE_ERROR: "  +  fastqFiles[i]);
					}
				}
			}
			
			// set outputfile - if supplied, check that it can be written to
			if (null != options.getOutputFileName()) {
				String optionsOutputFile = options.getOutputFileName();
				if (FileUtils.canFileBeWrittenTo(optionsOutputFile)) {
					outputFile = optionsOutputFile;
				} else {
					throw new Exception("OUTPUT_FILE_WRITE_ERROR");
				}
			}
			
			xmlFile = options.getXml();
			if (options.hasMinBinSizeOption()) {
				this.minBinSize = options.getMinBinSize().intValue();
			}
			logger.info("minBinSize is " + minBinSize);
			
			
				return engage();
			}
		return returnStatus;
	}

}
