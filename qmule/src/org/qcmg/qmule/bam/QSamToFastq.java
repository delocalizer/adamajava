/**
 * © Copyright The University of Queensland 2010-2014.
 * © Copyright QIMR Berghofer Medical Research Institute 2014-2016.
 *
 * This code is released under the terms outlined in the included LICENSE file.
 */
package org.qcmg.qmule.bam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.broadinstitute.barclay.argparser.Argument;
import org.qcmg.common.log.QLogger;
import org.qcmg.common.log.QLoggerFactory;
import org.qcmg.picard.SAMFileReaderFactory;
import org.qcmg.picard.fastq.QFastqWriter;
import org.qcmg.qmule.GetBamRecords;

import picard.PicardException;
import picard.cmdline.CommandLineProgram;
import picard.cmdline.StandardOptionDefinitions;
import htsjdk.samtools.util.IOUtil;

import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMUtils;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.StringUtil;


/**
 * $Id: SamToFastq.java 1235 2012-07-21 12:49:32Z tfenne $
 * <p/>
 * Extracts read sequences and qualities from the input SAM/BAM file and writes them into
 * the output file in Sanger fastq format.
 * See <a href="http://maq.sourceforge.net/fastq.shtml">MAQ FastQ specification</a> for details.
 * In the RC mode (default is True), if the read is aligned and the alignment is to the reverse strand on the genome,
 * the read's sequence from input sam file will be reverse-complemented prior to writing it to fastq in order restore correctly
 * the original read sequence as it was generated by the sequencer.
 */
public class QSamToFastq extends CommandLineProgram {
	
    public String USAGE = "Extracts read sequences and qualities from the input SAM/BAM file and writes them into "+
        "the output file in Sanger fastq format. In the RC mode (default is True), if the read is aligned and the alignment is to the reverse strand on the genome, "+
        "the read's sequence from input SAM file will be reverse-complemented prior to writing it to fastq in order restore correctly "+
        "the original read sequence as it was generated by the sequencer.";

    @Argument(doc="Input SAM/BAM file to extract reads from", shortName=StandardOptionDefinitions.INPUT_SHORT_NAME)
    public File INPUT ;

    @Argument(shortName="F", doc="Output fastq file (single-end fastq or, if paired, first end of the pair fastq).", mutex={"OUTPUT_PER_RG"})
    public File FASTQ ;

    @Argument(shortName="F2", doc="Output fastq file (if paired, second end of the pair fastq).", optional=true, mutex={"OUTPUT_PER_RG"})
    public File SECOND_END_FASTQ ;

    @Argument(shortName="OPRG", doc="Output a fastq file per read group (two fastq files per read group if the group is paired).", optional=true, mutex={"FASTQ", "SECOND_END_FASTQ"})
    public boolean OUTPUT_PER_RG ;

    @Argument(shortName="ODIR", doc="Directory in which to output the fastq file(s).  Used only when OUTPUT_PER_RG is true.", optional=true)
    public File OUTPUT_DIR;

    @Argument(shortName="RC", doc="Re-reverse bases and qualities of reads with negative strand flag set before writing them to fastq", optional=true)
    public boolean RE_REVERSE = true;

    @Argument(shortName="NON_PF", doc="Include non-PF reads from the SAM file into the output FASTQ files.")
    public boolean INCLUDE_NON_PF_READS = false;
    

    @Argument(shortName="CLIP_ATTR", doc="The attribute that stores the position at which " +
            "the SAM record should be clipped", optional=true)
    public String CLIPPING_ATTRIBUTE;

 
    @Argument(shortName="CLIP_ACT", doc="The action that should be taken with clipped reads: " +
            "'X' means the reads and qualities should be trimmed at the clipped position; " +
            "'N' means the bases should be changed to Ns in the clipped region; and any " +
            "integer means that the base qualities should be set to that value in the " +
            "clipped region.", optional=true)
    public String CLIPPING_ACTION;

    @Argument(shortName="R1_TRIM", doc="The number of bases to trim from the beginning of read 1.")
    public int READ1_TRIM = 0;

    @Argument(shortName="R1_MAX_BASES", doc="The maximum number of bases to write from read 1 after trimming. " +
            "If there are fewer than this many bases left after trimming, all will be written.  If this " +
            "value is null then all bases left after trimming will be written.", optional=true)
    public Integer READ1_MAX_BASES_TO_WRITE;

     
    @Argument(shortName="R2_TRIM", doc="The number of bases to trim from the beginning of read 2.")
    public int READ2_TRIM = 0;

    @Argument(shortName="R2_MAX_BASES", doc="The maximum number of bases to write from read 2 after trimming. " +
             "If there are fewer than this many bases left after trimming, all will be written.  If this " +
            "value is null then all bases left after trimming will be written.", optional=true)
    public Integer READ2_MAX_BASES_TO_WRITE;

    @Argument(doc="If true, include non-primary alignments in the output.  Support of non-primary alignments in SamToFastq " +
     "is not comprehensive, so there may be exceptions if this is set to true and there are paired reads with non-primary alignments.")
    public boolean INCLUDE_NON_PRIMARY_ALIGNMENTS = false;
    
    @Argument(doc="If true, include supplementary alignments in the output.  Support of supplementary alignments in SamToFastq " +
    	     "is not comprehensive, so there may be exceptions if this is set to true and there are paired reads with non-primary alignments.")    
    public boolean INCLUDE_SUPPLEMENTARY_READS = false;
    
    //new argument 
    @Argument(doc="If true, read id will be appended /1 for first of pair and /2 for second of pair. If false, read id will be keep same with BAM record id.")
    public boolean MARK_MATE = false;

    @Argument(doc="If true, set 'N' to fastq record base if SAM record missing base sequence; and then set '!' to base quality. If false, read base will be same with BAM record base.")
    public boolean NON_ATGC_2N = true;
    
    @Argument(shortName="log", doc="output a log file.")
    public String LOG_FILE = "qsamtofastq.log"; 
    
    private static QLogger logger;
    private static String[] args; //for log init only
    
    //count reads missing pair
    private AtomicLong fakeMateCount = new AtomicLong();
    private List<String> readName_mCount = new ArrayList<>(); 
    
    //count reads with missing base sequence
    private AtomicLong baseNCount = new AtomicLong();
    private List<String> readName_nCount = new ArrayList<>();
    private final int MAX_RECORD_COUNT = 10;
    
    //count reads with missing base sequence
    private AtomicLong nonPFCount = new AtomicLong();
   
    
    public static void main(final String[] argv) {
    	
     	args = argv;	//pass to log file
        System.exit(new QSamToFastq().instanceMain(argv));
    }

    @Override
	protected int doWork() {
      	//make log file
       	logger = QLoggerFactory.getLogger(GetBamRecords.class,  LOG_FILE, QLoggerFactory.DEFAULT_LEVEL.getName());    	
		logger.logInitialExecutionStats("CheckBam", CheckBam.class.getPackage().getImplementationVersion(), args);

    	
        IOUtil.assertFileIsReadable(INPUT);
        final SamReader reader =  SAMFileReaderFactory.createSAMFileReader( INPUT);
        
        //bamfile must be sorted by qurey name
        if( ! reader.getFileHeader().getSortOrder().equals(SortOrder.queryname)) {        	
        	throw new PicardException("input file is not sorted by " + SortOrder.queryname.name());
        }
        
        //final Map<String,SAMRecord> firstSeenMates = new HashMap<String,SAMRecord>();
        SAMRecord  firstRecord = null;
        final Map<SAMReadGroupRecord, List<FastqWriter>> writers = getWriters(reader.getFileHeader().getReadGroups());

        for (final SAMRecord currentRecord : reader) {
        	//debug 
        	System.out.println("debug: " + currentRecord.getReadName());
        	// Skip non-PF, secondary, supplementary reads as necessary
            if ((currentRecord.isSecondaryAlignment() && !INCLUDE_NON_PRIMARY_ALIGNMENTS) ||            		
            		(currentRecord.getReadFailsVendorQualityCheckFlag() && !INCLUDE_NON_PF_READS) ||
            		(currentRecord.getSupplementaryAlignmentFlag() && !INCLUDE_SUPPLEMENTARY_READS) ) {
            	nonPFCount.incrementAndGet();
                continue;
            }

            final List<FastqWriter> fq = writers.get(currentRecord.getReadGroup());

            if (currentRecord.getReadPairedFlag()) {            	
            	//record current read and continue to next read
            	if( firstRecord == null ) {
            		firstRecord = currentRecord;
            		continue;
            	}
           	            	
                //output pair after creating fake pair if first record missing pair
            	if( !assertPairedMates(firstRecord, currentRecord)) {
                	rescueLonelyRecord(  firstRecord,  writers); 
                	firstRecord = currentRecord;
                	continue;
            	}
                	
                //process paired reads	 
                if (fq.size() == 1) {
                    if (OUTPUT_PER_RG) {
                        try {
							fq.add(new QFastqWriter(makeReadGroupFile(currentRecord.getReadGroup(), "_2")));
						} catch (IOException e) {
							e.printStackTrace();
						}
                    } else {
                        throw new PicardException("Input contains paired reads but no SECOND_END_FASTQ specified.");
                    }
                }

                final SAMRecord read1 = currentRecord.getFirstOfPairFlag() ? currentRecord : firstRecord;
                final SAMRecord read2 = currentRecord.getFirstOfPairFlag() ? firstRecord : currentRecord;
                
                if(MARK_MATE) {
	                writeRecord(read1, 1, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
	                writeRecord(read2, 2, fq.get(1), READ2_TRIM, READ2_MAX_BASES_TO_WRITE);
                } else {
	                writeRecord(read1, null, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
	                writeRecord(read2, null, fq.get(1), READ2_TRIM, READ2_MAX_BASES_TO_WRITE);            	               	
                }
                
                //empty records, ready for next pair.
                firstRecord = null;
            } else {
                writeRecord(currentRecord, null, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
            }

        }        
        // Close all the fastq writers being careful to close each one only once!
        final IdentityHashMap<FastqWriter,FastqWriter> seen = new IdentityHashMap<FastqWriter, FastqWriter>();
        for (final List<FastqWriter> listOfWriters : writers.values()) {
            for (final FastqWriter w : listOfWriters) {
                if (!seen.containsKey(w)) {
                    w.close();
                    seen.put(w,w);
                }
            }
        }
    
        if (fakeMateCount.get() > 0) {
        	String str = fakeMateCount.get() + " fake mate read created. eg. " + readName_mCount.get(0) + "";
	        for( int i = 1; i < readName_mCount.size(); i ++ )  str += ", " + readName_mCount.get(i) ;
	        logger.info(str + ".");		        
	    }
        
        
        if (baseNCount.get() > 0) {
        	String str = baseNCount.get() + " reads missing base sequence, now N is added to fastq sequence with lowest base qulity value '!' . eg " + readName_nCount.get(0) + "";
	        for( int i = 1; i < readName_nCount.size(); i ++ )  str += ", " + readName_nCount.get(i) ;
	        logger.info(str + ".");		        
	    }     
        
        if (nonPFCount.get() > 0) logger.info(nonPFCount.get() + " reads are discarded due to marked as non-primary, secondary or supplementary.");
        
        logger.logFinalExecutionStats(0);

        return 0;
    }
    
  /**
   * Create an mate read with empty sequence and base, so any paired read can stay with its fake partner
   * @param read : read to be rescued
   * @param writers: a hash map of all writers
   */
    
    protected void rescueLonelyRecord(final SAMRecord read, Map<SAMReadGroupRecord, List<FastqWriter>> writers) {

    	//add N to missing read with lowest base quality value
    	SAMRecord emptyRead = new SAMRecord(read.getHeader());
    	emptyRead.setReadName(read.getReadName());
    	emptyRead.setReadBases( "N".getBytes() );
    	emptyRead.setBaseQualityString("!");
    	
        final List<FastqWriter> fq = writers.get(read.getReadGroup());
   	
        final SAMRecord read1 = read.getFirstOfPairFlag() ? read : emptyRead;
        final SAMRecord read2 = read.getFirstOfPairFlag() ? emptyRead : read;
        
        if(MARK_MATE) {
	        writeRecord(read1, 1, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
	        writeRecord(read2, 2, fq.get(1), READ2_TRIM, READ2_MAX_BASES_TO_WRITE);        
        } else {
	        writeRecord(read1, null, fq.get(0), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
	        writeRecord(read2, null, fq.get(1), READ2_TRIM, READ2_MAX_BASES_TO_WRITE);             	
        }
        
         //count reads missing pair
        fakeMateCount.incrementAndGet();
        if( readName_mCount.size() < MAX_RECORD_COUNT) {
        	readName_mCount.add(read.getReadName());
        }      
    }

    /**
     * Gets the pair of writers for a given read group or, if we are not sorting by read group,
     * just returns the single pair of writers.
     */
    protected Map<SAMReadGroupRecord, List<FastqWriter>> getWriters(final List<SAMReadGroupRecord> samReadGroupRecords) {

        final Map<SAMReadGroupRecord, List<FastqWriter>> writerMap = new HashMap<SAMReadGroupRecord, List<FastqWriter>>();

        if (!OUTPUT_PER_RG) {
            // If we're not outputting by read group, there's only
            // one writer for each end.
            final List<FastqWriter> fqw = new ArrayList<FastqWriter>();

            IOUtil.assertFileIsWritable(FASTQ);
            IOUtil.openFileForWriting(FASTQ);
            try {
				fqw.add(new QFastqWriter(FASTQ));
			} catch (IOException e1) {
				e1.printStackTrace();
			}

            if (SECOND_END_FASTQ != null) {
                IOUtil.assertFileIsWritable(SECOND_END_FASTQ);
                IOUtil.openFileForWriting(SECOND_END_FASTQ);
                try {
					fqw.add(new QFastqWriter(SECOND_END_FASTQ));
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
            // Store in map with null key, in case there are reads without read group.
            writerMap.put(null, fqw);
            // Also store for every read group in header.
            for (final SAMReadGroupRecord rg : samReadGroupRecords) {
                writerMap.put(rg, fqw);
            }
        } else {
            for (final SAMReadGroupRecord rg : samReadGroupRecords) {
                final List<FastqWriter> fqw = new ArrayList<FastqWriter>();

                try {
					fqw.add(new QFastqWriter(makeReadGroupFile(rg, "_1")));
				} catch (IOException e) {
					e.printStackTrace();
				}
                writerMap.put(rg, fqw);
            }
        }
        return writerMap;
    }
    

    protected File makeReadGroupFile(final SAMReadGroupRecord readGroup, final String preExtSuffix) {
        String fileName = readGroup.getPlatformUnit();
        if (fileName == null) fileName = readGroup.getReadGroupId();
        fileName = IOUtil.makeFileNameSafe(fileName);
        if(preExtSuffix != null) fileName += preExtSuffix;
        fileName += ".fastq";

        final File result = (OUTPUT_DIR != null)  ? new File(OUTPUT_DIR, fileName) : new File(fileName);
        IOUtil.assertFileIsWritable(result);
        return result;
    }

    protected void writeRecord(final SAMRecord read, final Integer mateNumber, final FastqWriter writer,
                     final int basesToTrim, final Integer maxBasesToWrite) {
        final String seqHeader = mateNumber==null ? read.getReadName() : read.getReadName() + "/"+ mateNumber;
              
        //convert base * to N and set lowest base quality score
        String readString = read.getReadString();
        String baseQualities = read.getBaseQualityString();

        if(readString.equals(SAMRecord.NULL_SEQUENCE_STRING) ) {
        	readString = "N";
        	baseQualities = "!";
        	
            //count reads missing pair
        	baseNCount.incrementAndGet();
            if( readName_nCount.size() < MAX_RECORD_COUNT) {
            	readName_nCount.add(read.getReadName());
            }        	
        }

        // If we're clipping, do the right thing to the bases or qualities
        if (CLIPPING_ATTRIBUTE != null) {
            final Integer clipPoint = (Integer)read.getAttribute(CLIPPING_ATTRIBUTE);
            if (clipPoint != null) {
                if (CLIPPING_ACTION.equalsIgnoreCase("X")) {
                    readString = clip(readString, clipPoint, null, !read.getReadNegativeStrandFlag());
                    baseQualities = clip(baseQualities, clipPoint, null, !read.getReadNegativeStrandFlag());

                }
                else if (CLIPPING_ACTION.equalsIgnoreCase("N")) {
                    readString = clip(readString, clipPoint, 'N', !read.getReadNegativeStrandFlag());
                }
                else {
                    final char newQual = SAMUtils.phredToFastq(
                    new byte[] { (byte)Integer.parseInt(CLIPPING_ACTION)}).charAt(0);
                    baseQualities = clip(baseQualities, clipPoint, newQual,!read.getReadNegativeStrandFlag());
                }
            }
        }
        if ( RE_REVERSE && read.getReadNegativeStrandFlag() ) {
            readString = SequenceUtil.reverseComplement(readString);
            baseQualities = StringUtil.reverseString(baseQualities);
        }
        if (basesToTrim > 0) {
            readString = readString.substring(basesToTrim);
            baseQualities = baseQualities.substring(basesToTrim);
        }

        if (maxBasesToWrite != null && maxBasesToWrite < readString.length()) {
            readString = readString.substring(0, maxBasesToWrite);
            baseQualities = baseQualities.substring(0, maxBasesToWrite);
        }       

        writer.write(new FastqRecord(seqHeader, readString, "", baseQualities));
    }

    /**
     * Utility method to handle the changes required to the base/quality strings by the clipping
     * parameters.
     *
     * @param src           The string to clip
     * @param point         The 1-based position of the first clipped base in the read
     * @param replacement   If non-null, the character to replace in the clipped positions
     *                      in the string (a quality score or 'N').  If null, just trim src
     * @param posStrand     Whether the read is on the positive strand
     * @return String       The clipped read or qualities
     */
    private String clip(final String src, final int point, final Character replacement, final boolean posStrand) {
        final int len = src.length();
        String result = posStrand ? src.substring(0, point-1) : src.substring(len-point+1);
        if (replacement != null) {
            if (posStrand) {
                for (int i = point; i <= len; i++ ) {
                    result += replacement;
                }
            }
            else {
                for (int i = 0; i <= len-point; i++) {
                    result = replacement + result;
                }
            }
        }
        return result;
    }

//    protected void assertPairedMates(final SAMRecord record1, final SAMRecord record2) {
//        if (! (record1.getFirstOfPairFlag() && record2.getSecondOfPairFlag() ||
//               record2.getFirstOfPairFlag() && record1.getSecondOfPairFlag() ) ) {
//            throw new PicardException("Illegal mate state: " + record1.getReadName());
//        }
//    }

    
	  protected boolean assertPairedMates(final SAMRecord record1, final SAMRecord record2) {
		  if (! (record1.getFirstOfPairFlag() && record2.getSecondOfPairFlag() ||
		         record2.getFirstOfPairFlag() && record1.getSecondOfPairFlag() ) ||
				 ! record1.getReadName().equals(record2.getReadName())  ) {
		      return false; 
		  }
		  
		  return true; 
	  }
    
    

    /**
    * Put any custom command-line validation in an override of this method.
    * clp is initialized at this point and can be used to print usage and access argv.
     * Any options set by command-line parser can be validated.
    * @return null if command line is valid.  If command line is invalid, returns an array of error
    * messages to be written to the appropriate place.
    */
    @Override
	protected String[] customCommandLineValidation() {
        if ((CLIPPING_ATTRIBUTE != null && CLIPPING_ACTION == null) ||
            (CLIPPING_ATTRIBUTE == null && CLIPPING_ACTION != null)) {
            return new String[] {
                    "Both or neither of CLIPPING_ATTRIBUTE and CLIPPING_ACTION should be set." };
        }
        if (CLIPPING_ACTION != null) {
            if (CLIPPING_ACTION.equals("N") || CLIPPING_ACTION.equals("X")) {
                // Do nothing, this is fine
            }
            else {
                try {
                    Integer.parseInt(CLIPPING_ACTION);
                }
                catch (NumberFormatException nfe) {
                    return new String[] {"CLIPPING ACTION must be one of: N, X, or an integer"};
                }
            }
        }
        if ((OUTPUT_PER_RG && OUTPUT_DIR == null) || ((!OUTPUT_PER_RG) && OUTPUT_DIR != null)) {
            return new String[] {
                    "If OUTPUT_PER_RG is true, then OUTPUT_DIR should be set. " +
                    "If " };
        }


        return null;
    }
}
