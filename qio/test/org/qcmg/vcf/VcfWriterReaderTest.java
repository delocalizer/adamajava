package org.qcmg.vcf;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.qcmg.common.util.FileUtils;
import org.qcmg.common.vcf.header.VcfHeader;
import org.qcmg.common.vcf.header.VcfHeaderUtils;
import org.qcmg.qio.vcf.VcfFileReader;
import org.qcmg.qio.vcf.VCFFileWriter;

public class VcfWriterReaderTest {
	
	public static final String[] vcfStrings = new String[] {"##test=test", VcfHeaderUtils.STANDARD_FINAL_HEADER_LINE};
	
	@Rule
	public  TemporaryFolder testFolder = new TemporaryFolder();
	
	@Test
	public void getHeaderFromZippedVcfFileUsingStreams() throws IOException {
		File file =  testFolder.newFile("header.vcf.gz");
		
		try(VCFFileWriter writer = new VCFFileWriter(file) ){
			 writer.addHeader(Arrays.stream(vcfStrings).collect(Collectors.joining("\n")));
		}
		assertEquals(true, FileUtils.isInputGZip(file) );
		
		/*
		 * Should be able to get the header back out
		 */
		VcfHeader header = null;
		try(VcfFileReader reader = VcfFileReader.createStream(file)){
			header = reader.getVcfHeader();
		}
		assertEquals(true, null != header);
		assertEquals(VcfHeaderUtils.STANDARD_FINAL_HEADER_LINE, header.getChrom().toString());
		assertEquals(1, header.getAllMetaRecords().size());
		assertEquals("##test=test", header.getAllMetaRecords().get(0).toString());
	}
	
	@Test
	public void getHeaderFromZippedVcfFile() throws IOException {
		File file =  testFolder.newFile("header.vcf.gz");
		
		try(VCFFileWriter writer = new VCFFileWriter(file) ){
			writer.addHeader(Arrays.stream(vcfStrings).collect(Collectors.joining("\n")));
		}
		assertEquals(true, FileUtils.isInputGZip(file) );
		
		
		/*
		 * Should be able to get the header back out
		 */
		VcfHeader header = null;
		
		try(VcfFileReader reader = new VcfFileReader(file) ){
			header = reader.getVcfHeader();
		}
		assertEquals(true, null != header);
		assertEquals(VcfHeaderUtils.STANDARD_FINAL_HEADER_LINE, header.getChrom().toString());
		assertEquals(1, header.getAllMetaRecords().size());
		assertEquals("##test=test", header.getAllMetaRecords().get(0).toString());
	}
	
	@Test
	public void testValidation() throws IOException  {
		String[] fnames = new String[] { "test.output.gz", "test.output" };
		String[] vcfStrings = new String[] {"#testing...", VcfHeaderUtils.STANDARD_FINAL_HEADER_LINE};
		for(int i = 0; i < fnames.length; i ++){ 
			File file = testFolder.newFile(fnames[i]);
			//create writer
			try(VCFFileWriter writer = new VCFFileWriter(file) ){
				 writer.addHeader(vcfStrings[i]);				
			} catch (Exception e) { fail(); }
			
			//check output type
			try {
				if( FileUtils.isFileNameGZip(file) )				
					Assert.assertTrue( FileUtils.isInputGZip(file) );
				else
					Assert.assertFalse( FileUtils.isInputGZip(file) );				
			} catch (IOException e) { fail(); }
			
			//read invaid vcf
			try(VcfFileReader reader = new VcfFileReader(file);){
				//file can be deleted here even without close but can't be closed if fail()	
				Assert.assertTrue( file.delete());				
				if(i == 0 ) fail();
				Assert.assertTrue( reader.getVcfHeader() != null);
			
			} catch (Exception e) {				 
				e.printStackTrace();
				if(i == 1 ) fail();
				//delete testing file which is invalid vcf, 
				Assert.assertTrue( file.delete());
			}						
		}			
	}

	@Test
	public void testCreateAppendVcfWriter() throws IOException {
		File file =  testFolder.newFile("output.vcf");
		
		//create new file 
		try(VCFFileWriter writer = new VCFFileWriter(file) ){
			 writer.addHeader(vcfStrings[0]);				
		} catch (Exception e) { fail(); }
		
		// read throw exception
		try(VcfFileReader reader = new VcfFileReader(file);){
			fail();
		} catch (Exception e) { }
		
		
		//append to file
		try(VCFFileWriter writer = VCFFileWriter.createAppendVcfWriter(file)   ){
			 writer.addHeader(vcfStrings[1]);				
		} catch (Exception e) { fail(); }
		
		//now it become a valid vcf file
		try(VcfFileReader reader = new VcfFileReader(file);){ 
			Assert.assertFalse( FileUtils.isInputGZip(file) );	
			Assert.assertTrue( reader.getVcfHeader().getAllMetaRecords().size() == 1);	
			Assert.assertTrue(file.delete());
			
		} catch (Exception e) {fail(); }		
		
	}

}
