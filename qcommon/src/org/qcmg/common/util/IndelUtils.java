/**
 * © Copyright QIMR Berghofer Medical Research Institute 2014-2016.
 *
 * This code is released under the terms outlined in the included LICENSE file.
*/
package org.qcmg.common.util;

import org.qcmg.common.string.StringUtils;
import org.qcmg.common.vcf.VcfFormatFieldRecord;
import org.qcmg.common.vcf.VcfRecord;
import org.qcmg.common.vcf.header.VcfHeaderUtils;
public class IndelUtils {
	public enum SVTYPE {
		
		SNP(1),DNP(2),TNP(3), ONP(4),INS(5),DEL(6),CTX(7),UNKNOWN(0);
		
		public final int order;
		SVTYPE(int od){this.order = od;}		
		public int getOrder(){return order; }
		static public int getSize() { return CTX.order + 1;}
	}		
	
	//qbasepileup indel vcf header info column ID
	public static final String INFO_END = "END"; 
	public static final String DESCRITPION_INFO_END = "End position of the variant described in this record"; 
	
	public static final String INFO_SVTYPE = "SVTYPE";
	public static final String DESCRITPION_INFO_SVTYPE = "Type of structural variant";

	public static final String INFO_SOMATIC = "SOMATIC";
	public static final String DESCRITPION_INFO_SOMATIC = "set to somatic unless there are more than three novel starts on normal BAM;"
			+ " or more than 10% imformative reads are supporting reads; or homopolymeric sequence exists on either side with nearby indels.";
		
	public static final String FILTER_COVN12 ="COVN12";
	public static final String DESCRITPION_FILTER_COVN12 = "For somatic calls: less than 12 reads coverage in normal BAM";
	
	public static final String FILTER_COVN8 = "COVN8";
	public static final String DESCRITPION_FILTER_COVN8 = "For germline calls: less than 8 reads coverage in normal";	
	
	public static final String FILTER_COVT = "COVT";
	public static final String DESCRITPION_FILTER_COVT = "For germline calls: less than 8 reads coverage in tumour";
	
	public static final String FILTER_HCOVN = "HCOVN";
	public static final String DESCRITPION_FILTER_HCOVN = "more than 1000 reads in normal BAM";
	
	public static final String FILTER_HCOVT = "HCOVT";
	public static final String DESCRITPION_FILTER_HCOVT = "more than 1000 reads in tumour BAM";
	
	public static final String FILTER_MIN = "MIN";
	public static final String DESCRITPION_FILTER_MIN = "For somatic calls: mutation also found in pileup of normal BAM";
	
	public static final String FILTER_NNS = "NNS";
	public static final String DESCRITPION_FILTER_NNS = "For somatic calls: less than 4 novel starts not considering read pair in tumour BAM";
	
	public static final String FILTER_TPART = "TPART";
	public static final String DESCRITPION_FILTER_TPART = "The number in the tumour partials column is >=3 and is >10% of the total reads at that position";

	public static final String FILTER_NPART = "NPART";
	public static final String DESCRITPION_FILTER_NPART = "The number in the normal partials column is >=3 and is >5% of the total reads at that position";

	public static final String FILTER_TBIAS = "TBIAS";
	public static final String DESCRITPION_FILTER_TBIAS = "For somatic calls: the supporting tumour reads value is >=3 and the count on one strand is =0 or >0 "
			+ "and is either <10% of supporting reads or >90% of supporting reads";

	public static final String FILTER_NBIAS = "NBIAS";
	public static final String DESCRITPION_FILTER_NBIAS = "For germline calls: the supporting normal reads value is >=3 and the count on one strand is =0 or >0 "
			+ "and is either <5% of supporting reads or >95% of supporting reads";
	
		 
//	public static final String FILTER_HOM = "HOM";
//	public static final String DESCRITPION_FILTER_HOM = "a digit number is attached on this FILTER id, eg. HOM24 means the nearby homopolymers sequence is 24 base long";
	
	public static final String INFO_NIOC = "NIOC";
	public static final String DESCRITPION_INFO_NIOC = "counts of nearby indels compare with total coverage";	
	
	public static final String INFO_SSOI = "SSOI";
	public static final String DESCRITPION_INFO_SSOI = "counts of strong support indels compare with total informative reads coverage";	

	
//	public static final String INFO_HOMCNTXT = "HOMCNTXT";
//	public static final String DESCRITPION_INFO_HOMCNTXT = "nearby reference sequence. if it is homopolymeric, the maximum repeated based counts will be added infront of sequence ";
	
	public static final String FORMAT_ACINDEL = "ACINDEL";
	public static final String DESCRITPION_FORMAT_ACINDEL = "counts of indels, follow formart:"
			+ "novelStarts,totalCoverage,informativeReadCount,strongSuportReadCount[forwardsuportReadCount,backwardsuportReadCount],suportReadCount[novelStarts],partialReadCount,nearbyIndelCount,nearybySoftclipCount";

	public static final int MAX_INDEL_LENGTH = 200;

	/**
	 * 
	 * @param ref: reference base from vcf record 4th column;
	 * @param alt: single alleles base from vcf record 5th column
	 * @return variant type, whether it is SNP, MNP, INSERTION, DELETION or TRANSLOCATION
	 */
	public static SVTYPE getVariantType(String ref, String alts){
		if (StringUtils.isNullOrEmpty(alts) || StringUtils.isNullOrEmpty(ref)) {
			throw new IllegalArgumentException("Null or empty alt and/or ref passed to getVariantType. alt: " + alts + ", ref: " + ref);
		}
		/*
		 * Only deal with alts of same length for now
		 */
		String alt = getFirstAltIfSameLength(alts);
		if (alt != null) {
			int refLen = ref.length();
			int altLen = alt.length();
			 //snp
			 if ( refLen == altLen ) {
				 switch (refLen) {
					 case 1: return SVTYPE.SNP;	
					 case 2: return SVTYPE.DNP;	
					 case 3: return SVTYPE.TNP;
					 default: return SVTYPE.ONP;
				 }
			 }
			 
			 // insertions  
			 if ( altLen <  MAX_INDEL_LENGTH &&  altLen > refLen && refLen == 1)  
				 return  SVTYPE.INS;	
			 
			 // deletions
			 if (refLen <  MAX_INDEL_LENGTH && altLen < refLen && altLen == 1)  
				 return  SVTYPE.DEL;
			  			 
			 //complicated variants
			 if(altLen <  MAX_INDEL_LENGTH && refLen <  MAX_INDEL_LENGTH && refLen != 1 && altLen != 1)
				 return SVTYPE.CTX; 
		 }
		return SVTYPE.UNKNOWN;	
	}
	
	/**
	 * Returns true if all the alts are the same length as the ref, indicating that the alt alleles are all of the same type (ie. all snp's, or all compound snps)
	 * @param ref
	 * @param alts
	 * @return
	 */
	public static boolean refSameLengthAsAlts(String ref, String alts) {
		if (StringUtils.isNullOrEmpty(alts) || StringUtils.isNullOrEmpty(ref)) {
			throw new IllegalArgumentException("Null or empty alt and/or ref passed to IndelUtils.refSameLengthAsAlts. alts: " + alts+ ", ref: " + ref);
		}
		
		String alt = getFirstAltIfSameLength(alts);
		if (alt != null) {			
			return alt.length() == ref.length();
		}
		return false;
	}
	
//	/**
//	 * Returns true if alts is made up of a single alt (ie. no commas in alts), or if the comma split list of strings are all the same length
//	 * @param alts
//	 * @return
//	 */
//	public static boolean areAltsSameLength(String alts) {
//		if (StringUtils.isNullOrEmpty(alts)) {
//			throw new IllegalArgumentException("Null or empty alts string passed to IndelUtils.areAltsSameLength");
//		}
//		int commaIndex = alts.indexOf(Constants.COMMA);
//		if (commaIndex == -1) {
//			return true;
//		}
//		String [] aAlts = alts.split(Constants.COMMA_STRING);
//		int len = aAlts[0].length();
//		for (String alt : aAlts) {
//			if (alt.length() != len) {
//				return false;
//			}
//		}
//		return true;
//	}
	
	/**
	 * 
	 * @param alts : single or multi alleles string 
	 * @return the first allele if alts is made up of a single alt (ie. no commas in alts), or if the comma split list of strings are all the same length;
	 *  teturn null if multi alleles with different length
	 */
	
	public static String getFirstAltIfSameLength(String alts) {
		if (StringUtils.isNullOrEmpty(alts)) {
			throw new IllegalArgumentException("Null or empty alts string passed to IndelUtils.areAltsSameLength");
		}
		
		//single allele
		int commaIndex = alts.indexOf(Constants.COMMA);
		if (commaIndex == -1) {
			return alts;
		}
		
		//mutli alleles
		String [] aAlts = alts.split(Constants.COMMA_STRING);
		int len = aAlts[0].length();
		for (String alt : aAlts) {
			if (alt.length() != len) {
				return null;
			}
		}
		return aAlts[0];
	}
	
	/**
	 * 
	 * @param ref
	 * @return
	 */
	public static String getFullChromosome(String ref) {
		/*
		 * Deal with MT special case first
		 */
        if (ref.equals("chrM") || ref.equals("M") || ref.equals("MT")) {
            return "chrMT";
        }
        
        if (ref.startsWith(Constants.CHR)) {
        		return ref;
        	}
		
		if (ref.equals("X") || ref.equals("Y")) {
			return "chr" + ref;
		}
		
		/*
		 * If ref is an integer less than 23, slap "chr" in front of it
		 */
		try {
			if (Integer.parseInt(ref) < 23) {
				return Constants.CHR + ref;
			}
		} catch (NumberFormatException nfe) {
			// don't do anything here - will return the original reference
		}
		
		return ref;
	}

	/**
	 * 
	 * @param re: VcfFormatFieldRecord
	 * @param ref: reference base from vcf record column 5
	 * @param alt
	 * @return genotyp string. eg. A/T; return null if GT field is not exsit or empty
	 */
	public static String getGenotypeDetails(VcfFormatFieldRecord re, String ref, String alt){
		//if "GT" field is not exist, do nothing
		String sgt = re.getField(VcfHeaderUtils.FORMAT_GENOTYPE) ;
		String gd = null;
		if( ! StringUtils.isNullOrEmpty(sgt) && !sgt.equals(Constants.MISSING_DATA_STRING)){		
			boolean isbar = sgt.contains(Constants.BAR_STRING);
			String[] gts = isbar? sgt.split(Constants.BAR_STRING) : sgt.split("\\/"); 
			String[] sgd = new String[gts.length];
	
			String[] alts = alt.split(Constants.COMMA_STRING);
			//only allow three multi-allele, otherwise too confuse
			for(int j = 0; j < gts.length; j ++) 
				switch (gts[j].trim()){
					case "0": sgd[j] = ref; 
					break;
					case "1": sgd[j] = (alts.length >= 1) ? alts[0] : Constants.MISSING_DATA_STRING; 
					break;
					case "2": sgd[j] = (alts.length >= 2) ? alts[1] : Constants.MISSING_DATA_STRING; 
					break;
					case "3": sgd[j] = (alts.length >= 3) ? alts[2] : Constants.MISSING_DATA_STRING; 
					break;
					default:
						sgd[j] = Constants.MISSING_DATA_STRING; 
				}				
			gd = isbar? String.join("|", sgd) : String.join(Constants.SLASH_STRING, sgd);
		}		
		return gd;
	}

	public static String getMotif(String ref, String alt ){
		return getMotif(ref, alt, getVariantType(ref, alt));
	}
	public static String getMotif(String ref, String alt , SVTYPE type){
		if(type.equals(SVTYPE.INS))
			return alt.substring(1);			 
		else if(type.equals(SVTYPE.DEL))
			return ref.substring(1);
		else if(type.equals(SVTYPE.UNKNOWN))
			return null; 
		
		return ref;  //return ref for snp, mnp 		
	}
	
	/**
	 * If INSERTION, return -, if DELETION return ref minus first char else return ref
	 * 
	 * @param ref
	 * @param type
	 * @return
	 */
	public static String getRefForIndels(String ref, final SVTYPE type){
		if(type.equals(SVTYPE.DEL) ) {
			if ( ! StringUtils.isNullOrEmpty(ref)) {
				return  ref.substring(1); //remove heading base
			}
		} else if(type.equals(SVTYPE.INS)) {
			return "-"; //replace heading base with "-"
		}
		return ref;
	}

}
