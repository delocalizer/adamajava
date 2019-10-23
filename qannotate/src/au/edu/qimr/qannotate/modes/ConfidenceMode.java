/**
 * © Copyright QIMR Berghofer Medical Research Institute 2014-2016.
 *
 * This code is released under the terms outlined in the included LICENSE file.
*/
package au.edu.qimr.qannotate.modes;

import gnu.trove.list.TShortList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.qcmg.common.log.QLogger;
import org.qcmg.common.log.QLoggerFactory;
import org.qcmg.common.string.StringUtils;
import org.qcmg.common.util.AccumulatorUtils;
import org.qcmg.common.util.Constants;
import org.qcmg.common.util.SnpUtils;
import org.qcmg.common.vcf.VcfFileMeta;
import org.qcmg.common.vcf.VcfFormatFieldRecord;
import org.qcmg.common.vcf.VcfInfoFieldRecord;
import org.qcmg.common.vcf.VcfRecord;
import org.qcmg.common.vcf.VcfUtils;
import org.qcmg.common.vcf.header.VcfHeaderUtils;

import au.edu.qimr.qannotate.Options;

/**
 * @author christix
 *
 */

public class ConfidenceMode extends AbstractMode{
	private final QLogger logger = QLoggerFactory.getLogger(ConfidenceMode.class);
	
	public static final int HIGH_CONF_NOVEL_STARTS_PASSING_SCORE = 4;
	public static final int LOW_CONF_NOVEL_STARTS_PASSING_SCORE = 4;
	
	public static final int HIGH_CONF_ALT_FREQ_PASSING_SCORE = 5;	
	public static final int LOW_CONF_ALT_FREQ_PASSING_SCORE = 4;	
	
	public static final int CONTROL_COVERAGE_MIN_VALUE_SOMATIC_CALL = 12;
	public static final int CONTROL_COVERAGE_MIN_VALUE = 8;
	public static final int TEST_COVERAGE_MIN_VALUE = 8;
	
	public static final int MUTATION_IN_NORMAL_MIN_PERCENTAGE = 3;		// setting this to 3 to mirror existing prod pipeline - was 5
	public static final int MUTATION_IN_NORMAL_MIN_COVERAGE = 2;		// set this to 2, meaning that if defaults are used it will be max(2, 3%) that is used
	
	public static final int sBiasAltPercentage = 5;
	public static final int sBiasCovPercentage = 5;
	
	public static final int MIUN_CUTOFF = 2;	// based on existing values
	
	//filters 
	
	public static final String DESCRIPTION_INFO_CONFIDENCE = String.format( "set to HIGH if the variants passed all filter, "
			+ "appeared on more than %d novel stars reads and more than %d reads contains variants, is adjacent to reference sequence with less than %d homopolymer base; "
			+ "Or set to LOW if the variants passed MIUN/MIN/GERM filter, appeared on more than %d novel stars reads and more than %d reads contains variants;"
			+ "Otherwise set to Zero if the variants didn't matched one of above conditions.",
			HIGH_CONF_NOVEL_STARTS_PASSING_SCORE, HIGH_CONF_ALT_FREQ_PASSING_SCORE,IndelConfidenceMode.DEFAULT_HOMN,  LOW_CONF_NOVEL_STARTS_PASSING_SCORE, LOW_CONF_ALT_FREQ_PASSING_SCORE);


	private TShortList testCols;
	private TShortList controlCols;
	private VcfFileMeta meta;
	
	private int nnsCount = HIGH_CONF_NOVEL_STARTS_PASSING_SCORE;
	private int mrCount = HIGH_CONF_ALT_FREQ_PASSING_SCORE;
	
	private int controlCovCutoffForSomaticCalls = CONTROL_COVERAGE_MIN_VALUE_SOMATIC_CALL;
	private int controlCovCutoff = CONTROL_COVERAGE_MIN_VALUE;
	private int testCovCutoff = TEST_COVERAGE_MIN_VALUE;
	
	private int minCov = 0;
	
	private List<String> filtersToIgnore = new ArrayList<>();
	private double mrPercentage = 0.0f;
	
	private int miunCutoff = MIUN_CUTOFF;
	private int minCutoff = MUTATION_IN_NORMAL_MIN_COVERAGE;
	private double minPercentage = MUTATION_IN_NORMAL_MIN_PERCENTAGE;
	
	//for unit testing
	ConfidenceMode(){
//		this.testCols = new TShortArrayList(testCol);
//		this.controlCols = new TShortArrayList(controlCol);
	}
	ConfidenceMode(VcfFileMeta m){
		this.meta = m;
		testCols = meta.getAllTestPositions();
		controlCols = meta.getAllControlPositions();
	}

	
	public ConfidenceMode( Options options) throws Exception{				 
		logger.tool("input: " + options.getInputFileName());
        logger.tool("output annotated records: " + options.getOutputFileName());
        logger.tool("logger file " + options.getLogFileName());
        logger.tool("logger level " + (options.getLogLevel() == null ? QLoggerFactory.DEFAULT_LEVEL.getName() :  options.getLogLevel()));
 		
		loadVcfRecordsFromFile(new File( options.getInputFileName())   );	
		
		options.getNNSCount().ifPresent(i -> nnsCount = i.intValue());
		options.getMRCount().ifPresent(i -> mrCount = i.intValue());
		options.getControlCutoff().ifPresent(i -> controlCovCutoff = i.intValue());
		options.getControlCutoffForSomatic().ifPresent(i -> controlCovCutoffForSomaticCalls = i.intValue());
		options.getTestCutoff().ifPresent(i -> testCovCutoff = i.intValue());
		options.getMRPercentage().ifPresent(i -> mrPercentage = i.floatValue());
		options.getMIUNCutoff().ifPresent(i -> miunCutoff = i.intValue());
		options.getMINCutoff().ifPresent(i -> minCutoff = i.intValue());
		options.getMINPercentage().ifPresent(i -> minPercentage = i.floatValue());
		filtersToIgnore = options.getFiltersToIgnore();
		logger.tool("Number of Novel Starts filter value: " + nnsCount);
		logger.tool("Number of Mutant Reads filter value: " + mrCount);
		logger.tool("Percentage of Mutant Reads filter value: " + mrPercentage);
		logger.tool("Control coverage minimum value: " + controlCovCutoff);
		logger.tool("Control coverage minimum value (for SOMATIC calls): " + controlCovCutoffForSomaticCalls);
		logger.tool("Test coverage minimum value: " + testCovCutoff);
		logger.tool("Mutation In Unfiltered Normal (MIUN) will be applied if the Failed Filter (FF) format field contains more than " + miunCutoff + " occurrences of the alt in the normal (control)");
		logger.tool("Mutation In Normal (MIN) will be applied if number of alt reads in the normnal (control) are greater than or equal to " + minCutoff + " OR greather than or equal to " + minPercentage +"% of total reads");
		logger.tool("Filters to ignore: " + filtersToIgnore.stream().collect(Collectors.joining(", ")));
		
		minCov = Math.min(controlCovCutoff, testCovCutoff);

		//get control and test sample column; here use the header from inputRecord(...)
		meta = new VcfFileMeta(header);
		logger.tool("meta: " + meta.getType());
		testCols = meta.getAllTestPositions();
		controlCols = meta.getAllControlPositions();

		addAnnotation();
		addVcfHeaderFilters();
		reheader(options.getCommandLine(),options.getInputFileName());	
		writeVCF(new File(options.getOutputFileName()) );
	}
	
	public void addVcfHeaderFilters() {
		header.addFilter(VcfHeaderUtils.FILTER_COVERAGE, VcfHeaderUtils.FILTER_COVERAGE_DESC
				+ ", test coverage minimum value: " + testCovCutoff + ", control coverage minimum value (somatic/germline): "
				+ controlCovCutoffForSomaticCalls + "/" + controlCovCutoff);
		header.addFilter(VcfHeaderUtils.FILTER_MUTATION_IN_NORMAL,"Mutation also found in pileup of normal (>= " + minPercentage + "% of reads)");
		header.addFilter(VcfHeaderUtils.FILTER_MUTATION_IN_UNFILTERED_NORMAL,"Mutation also found in pileup of unfiltered normal (>= " + miunCutoff + " reads)");  
		header.addFilter(VcfHeaderUtils.FILTER_NOVEL_STARTS,"Less than " + nnsCount + " novel starts not considering read pair");
		header.addFilter(VcfHeaderUtils.FILTER_MUTANT_READS,"Less than " + (mrPercentage > 0.0f ? mrPercentage +"%" : mrCount) + " mutant reads");
		header.addFilter(VcfHeaderUtils.FILTER_STRAND_BIAS_ALT,"Alternate allele on only one strand (or percentage alternate allele on other strand is less than " + sBiasAltPercentage + "%)"); 
		header.addFilter(VcfHeaderUtils.FILTER_STRAND_BIAS_COV,"Sequence coverage on only one strand (or percentage coverage on other strand is less than " + sBiasCovPercentage + "%)");
		header.addFilter(VcfHeaderUtils.FILTER_END_OF_READ, VcfHeaderUtils.FILTER_END_OF_READ_DESC);
	}

	public void setMRPercentage(double perc) {
		mrPercentage = perc;
	}
	public void setNNSCount(int i) {
		nnsCount = i;
	}
	public void setFiltersToIgnore(List<String> l) {
		filtersToIgnore = l;
	}

	void addAnnotation() {
		
		int pass = 0;
		int fail = 0;
		
		final boolean percentageMode = mrPercentage > 0.0f;
		
		//check high, low nns...
		for (List<VcfRecord> vcfs : positionRecordMap.values()) {
			for(VcfRecord vcf : vcfs){
				
				Map<String, String[]> ffMap = vcf.getFormatFieldsAsMap();
				
				boolean isSomatic = VcfUtils.isRecordSomatic(vcf.getInfo(), ffMap);
				String [] alts = vcf.getAlt().split(Constants.COMMA_STRING);
				
				/*
				 * We will look at each sample in isolation
				 * if no genotype is present, skip the sample
				 * If we have a 0/0 genotype, and the sample FT is ., and the coverage is adequate, set it to pass
				 * if we have any other genotype (ie, the sample is showing a mutation), check coverage, MR and NNS and existing FT fields. If within our acceptable limits, set FT to PASS
				 */
				
				VcfInfoFieldRecord info = vcf.getInfoRecord();
				int lhomo = (info.getField(VcfHeaderUtils.INFO_HOM) == null)? 1 :
					StringUtils.string2Number(info.getField(VcfHeaderUtils.INFO_HOM).split(Constants.COMMA_STRING)[0], Integer.class);
				
				
				String [] gtArray = ffMap.get(VcfHeaderUtils.FORMAT_GENOTYPE);
				String [] nnsArr = ffMap.get(VcfHeaderUtils.FILTER_NOVEL_STARTS);
//				String [] mrArr = ffMap.get(VcfHeaderUtils.FORMAT_MUTANT_READS);
				String [] filterArr = ffMap.get(VcfHeaderUtils.FORMAT_FILTER);
				String [] covArr = ffMap.get(VcfHeaderUtils.FORMAT_READ_DEPTH);
				String [] ccmArr = ffMap.get(VcfHeaderUtils.FORMAT_CCM);
				String [] oabsArr = ffMap.get(VcfHeaderUtils.FORMAT_OBSERVED_ALLELES_BY_STRAND);
				String [] gqArr = ffMap.get(VcfHeaderUtils.FORMAT_GENOTYPE_QUALITY);
				String [] adArr = ffMap.get(VcfHeaderUtils.FORMAT_ALLELIC_DEPTHS);
				String [] infArr = ffMap.get(VcfHeaderUtils.FORMAT_INFO);
				String [] eorArr = ffMap.get(VcfHeaderUtils.FORMAT_END_OF_READ);
				String [] ffArr = ffMap.get(VcfHeaderUtils.FORMAT_FF);
				if (covArr == null || covArr.length == 0) {
					logger.warn("no coverage values for vcf record!!!: " + vcf);
					continue;
				}
				if (null == filterArr || filterArr.length == 0) {
					continue;
				}
				
				for (int i = 0 ; i < gtArray.length ; i++) {
					
					
					String inf = null != infArr && infArr.length > i ? infArr[i] : null;
					/*
					 * for No Call in GATK, set filter to PASS
					 */
					if (VcfHeaderUtils.FORMAT_NCIG.equals(inf)) {
						filterArr[i] = VcfHeaderUtils.FILTER_PASS;
						continue;
					}
					
					boolean isControl =  controlCols != null && controlCols.contains((short) (i+1));
					/*
					 * add all failed filters to FT field
					 */
					StringBuilder fSb = new StringBuilder();
					
					
					/*
					 * coverage next - needs to be >= the min coverage value
					 */
					String covS = covArr[i];
					boolean isGATKCall = null != gqArr && ! StringUtils.isNullOrEmptyOrMissingData(gqArr[i]);
					int cov = StringUtils.isNullOrEmptyOrMissingData(covS) ? 0 :Integer.parseInt(covS);
					
					if (cov < minCov || cov < (isControl ? controlCovCutoff : testCovCutoff)) {
						StringUtils.updateStringBuilder(fSb, VcfHeaderUtils.FILTER_COVERAGE, Constants.SEMI_COLON);
					}
					
					Map<String, int[]> alleleDist = (null != oabsArr && oabsArr.length >= i) ? VcfUtils.getAllelicCoverageWithStrand(oabsArr[i]) : Collections.emptyMap();
					/*
					 * MIN next - only do this for control when we have a somatic call
					 * need OABS field to be able to do this.
					 */
					if (isControl && isSomatic && ! isGATKCall) {
						
						checkMIN(alts, cov, alleleDist, fSb, minCutoff, (float) minPercentage);
						
						/*
						 * look for MIUN, but only if we don't already have MIN
						 */
						if ( ! fSb.toString().contains(VcfHeaderUtils.FILTER_MUTATION_IN_NORMAL)) {
							if (null != ffArr && ffArr.length > i) {
								String failedFilter = ffArr[i];
								checkMIUN(alts, failedFilter, fSb, miunCutoff);
							}
						}
						if ( ! fSb.toString().contains(VcfHeaderUtils.FILTER_COVERAGE) && cov < controlCovCutoffForSomaticCalls) {
							StringUtils.updateStringBuilder(fSb, VcfHeaderUtils.FILTER_COVERAGE, Constants.SEMI_COLON);
						}
					}
						
					/*
					 * SBIASALT and SBIASCOV - do for all samples
					 */
					String gt = gtArray[i];
					if ( ! StringUtils.isNullOrEmptyOrMissingData(gt)) {
						if ( ! "0/0".equals(gt)) {
							int index = gt.indexOf(Constants.SLASH);
							int []  gts = new int[] {Integer.parseInt(gt.substring(0,index)), Integer.parseInt(gt.substring(index + 1))};
							
							if ( ! alleleDist.isEmpty() && ! isGATKCall) {
								checkStrandBias(alts, fSb, alleleDist, gts, sBiasCovPercentage, sBiasAltPercentage);
							}
							
							/*
							 * HOM
							 */
							checkHOM(fSb, lhomo, IndelConfidenceMode.DEFAULT_HOMN);
							
							/*
							 * check mutant read count and novel starts
							 */
							if ( ! isGATKCall) {
								checkNNS(nnsArr[i], fSb, nnsCount);
							}
							
							if (applyMutantReadFilter(gts, adArr[i], percentageMode ? (int)(mrPercentage * cov) : mrCount)) {
								StringUtils.updateStringBuilder(fSb, VcfHeaderUtils.FORMAT_MUTANT_READS, Constants.SEMI_COLON);
							}
							
							/*
							 * end of read check
							 */
							if ( ! isGATKCall && null != eorArr) {
								int eor = endsOfReads(alts, gt, alleleDist, eorArr[i]);
								if (eor > 0) {
									StringUtils.updateStringBuilder(fSb, "5BP=" + eor, Constants.SEMI_COLON);
								}
							}
						}
					}
						
					filterArr[i] = fSb.length() == 0 ? VcfHeaderUtils.FILTER_PASS : fSb.toString();
					/*
					 * deal with homozygous loss instances where we potentially have no coverage in test - still mark as a pass
					 */
					if (null != ccmArr) {
						int ccm = Integer.parseInt(ccmArr[i]);
						if (ccm == 11 || ccm == 21 || ccm == 31 || ccm == 41) {
							filterArr[i] = VcfHeaderUtils.FILTER_PASS;
						}
					}
				}
				
				/*
				 * update vcf record with (possibly) updated ffs
				 */
				vcf.setFormatFields(VcfUtils.convertFFMapToList(ffMap));
				
				if (Arrays.stream(filterArr).distinct().count() == 1 && filterArr[0].equals(VcfHeaderUtils.FILTER_PASS)) {
					pass++;
				} else {
					fail++;
				}
			}
		}
		
		logger.info("Confidence breakdown, pass: " + pass + ", fail: " + fail );
 
		//add header line  set number to 1
		if (null != header ) {
			header.addInfo(VcfHeaderUtils.INFO_CONFIDENCE, "1", "String", DESCRIPTION_INFO_CONFIDENCE);
		}
	}


	/**
	 * @param alts
	 * @param fSb
	 * @param alleleDist
	 * @param gt
	 */
	public static void checkStrandBias(String[] alts, StringBuilder fSb, Map<String, int[]> alleleDist, int[] gts, int sBiasCovPercentage, int sBiasAltPercentage) {

		AtomicInteger fsCount = new AtomicInteger();
		AtomicInteger rsCount = new AtomicInteger();
		alleleDist.values().stream().forEach(a -> {fsCount.addAndGet(a[0]); rsCount.addAndGet(a[1]);});
		boolean sbiasCov = ! AccumulatorUtils.areBothStrandsRepresented(fsCount, rsCount, sBiasCovPercentage);
		
		
		for (int gtI : gts) {
			if (gtI > 0) {
				int [] iArray = alleleDist.get(alts[gtI - 1]);
				int min = Math.min(iArray[0], iArray[1]);
				
				if ( ((double) min / (iArray[0] + iArray[1])) * 100 < sBiasAltPercentage) {
					StringUtils.updateStringBuilder(fSb, sbiasCov ? SnpUtils.STRAND_BIAS_COVERAGE : SnpUtils.STRAND_BIAS_ALT, Constants.SEMI_COLON);
					break;
				}
			}
		}
	}
	
	public static void checkNNS(String nnsString, StringBuilder sb, int nnsCount) {
		int [] nns = getFieldOfInts(nnsString);
		if ( ! allValuesAboveThreshold(nns, nnsCount)) {
			StringUtils.updateStringBuilder(sb, VcfHeaderUtils.FILTER_NOVEL_STARTS, Constants.SEMI_COLON);
		}
	}
	
	public static void checkMIN(String [] alts, int coverage, Map<String, int[]> alleleDist, StringBuilder sb, int minCutoff, float minPercentage) {
		if (null != alts && null != alleleDist) {
			for (String alt : alts) {
				int altCov = Arrays.stream(alleleDist.getOrDefault(alt, new int[]{0,0})).sum();
				boolean min = VcfUtils.mutationInNormal(altCov, coverage, minPercentage, minCutoff);
				if (min) {
					StringUtils.updateStringBuilder(sb, VcfHeaderUtils.FILTER_MUTATION_IN_NORMAL, Constants.SEMI_COLON);
					break;
				}
			}
		}
	}
	
	public static void checkMIUN(String [] alts, String failedFilter, StringBuilder sb, int miunCutoff) {
		if (null != alts && ! StringUtils.isNullOrEmptyOrMissingData(failedFilter)) {
			for (String alt : alts) {
				int altIndex = failedFilter.indexOf(alt);
				if (altIndex > -1) {
					/*
					 * bases are separated by colons
					 */
					int semiColonIndex = failedFilter.indexOf(Constants.SEMI_COLON, altIndex);
					int failedFilterCount = Integer.parseInt(failedFilter.substring(altIndex + alt.length(), semiColonIndex > -1 ? semiColonIndex : failedFilter.length()));
					if (failedFilterCount >= miunCutoff) {
						StringUtils.updateStringBuilder(sb, VcfHeaderUtils.FILTER_MUTATION_IN_UNFILTERED_NORMAL, Constants.SEMI_COLON);
						break;
					}
				}
			}
		}
	}
	
	public static int endsOfReads(String [] alts, String gt, Map<String, int[]> oabsMap, String eor) {
		if ((null == oabsMap || oabsMap.isEmpty()) 
				|| StringUtils.isNullOrEmptyOrMissingData(eor)
				|| (null == alts || alts.length == 0)
				|| "0/0".equals(gt)
				|| "./.".equals(gt)
				) {
			return 0;
		}
		
		int i = 1;
		int maxBP = 0;
		Map<String, int[]> eorMap = VcfUtils.getAllelicCoverageWithStrand(eor);
		for (String alt : alts) {
			if (gt.contains(""+i)) {
				int [] altCov = oabsMap.getOrDefault(alt, new int [] {0,0});
				int [] altCovEOR = eorMap.getOrDefault(alt, new int [] {0,0});
				int middleOfReadForwardStrand = altCov[0] - altCovEOR[0];
				int middleOfReadReverseStrand = altCov[1] - altCovEOR[1];
				int middleOfReadCount = middleOfReadForwardStrand + middleOfReadReverseStrand;
				int endOfReadCount = altCovEOR[0] +  altCovEOR[1];
				
				if (middleOfReadCount >= 5 && (middleOfReadReverseStrand > 0 && middleOfReadForwardStrand > 0)) {
					// all good
				} else {
					if ((endOfReadCount) > maxBP) {
						maxBP = endOfReadCount;
					}
				}
			}
			i++;
		}
		return maxBP;
	}
	
	/**
	 * Check that if gt field contains a non-ref number that the corresponding AD value is above (or equal to) the cutoff
	 * @param gt
	 * @param adField
	 * @param mrCutoff
	 * @return
	 */
	public static boolean applyMutantReadFilter(int [] gts, String ad, int mrCutoff) {
		
		if ( null != gts && gts.length > 0 && ! StringUtils.isNullOrEmptyOrMissingData(ad)) {
			
			String [] adArray = ad.split(Constants.COMMA_STRING);
			if (null != adArray && adArray.length > 1) {
				
				for (int i : gts) {
					if (i > 0 && i < adArray.length && Integer.parseInt(adArray[i]) < mrCutoff) {
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	public static boolean allValuesAboveThreshold(int[] values, int threshold) {
		return Arrays.stream(values).allMatch(i -> i >= threshold);
	}
	public static boolean allValuesAboveThreshold(int[] values, int coverage, double percentageCutoff) {
		return Arrays.stream(values).allMatch(i -> ((double)i / coverage) * 100 >= percentageCutoff);
	}
	
	public static void checkHOM(StringBuilder sb, int homCount, int acceptableHomCount) {
		/*
		 * HOM
		 */
		if (homCount > 0 && homCount >= acceptableHomCount) {
			StringUtils.updateStringBuilder(sb, VcfHeaderUtils.INFO_HOM, Constants.SEMI_COLON);
		}
	}

	 public static int [] getFieldOfInts(VcfFormatFieldRecord formatField ,String key) {
		 String value = formatField.getField(key);
		 return getFieldOfInts(value);
	 }
	 
	 public static int [] getFieldOfInts(String value) {
		 if (StringUtils.isNullOrEmptyOrMissingData(value)) {
			 return new int[]{0};
		 }
		 int cI = value.indexOf(Constants.COMMA);
		 if (cI == -1) return new int[] {Integer.parseInt(value)};
		 return new int[]{Integer.parseInt(value.substring(0,cI)), Integer.parseInt(value.substring(cI + 1))}; 
	 }
	 
	 public static int [] getAltCoveragesFromADField(String ad) {
		 if (StringUtils.isNullOrEmptyOrMissingData(ad)) {
			 return new int[]{0};
		 }
		 String [] adArray = ad.split(Constants.COMMA_STRING);
		 if (adArray.length < 2) {
			 return new int[]{0};
		 }
		 int [] adIntArray = new int[adArray.length -1];
		 for (int i = 1 ; i < adArray.length ; i++) {
			 adIntArray[i-1] = Integer.parseInt(adArray[i]);
		 }
		 return adIntArray;
	 }
	 
	@Override
	void addAnnotation(String dbfile) throws IOException {
		// TODO Auto-generated method stub		
	}  
}	
	
