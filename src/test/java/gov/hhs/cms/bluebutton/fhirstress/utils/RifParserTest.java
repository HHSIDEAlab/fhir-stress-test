package gov.hhs.cms.bluebutton.fhirstress.utils;

import java.net.URISyntaxException;

import org.junit.*;

import gov.hhs.cms.bluebutton.data.model.rif.Beneficiary;
import gov.hhs.cms.bluebutton.data.model.rif.RifFileType;
import gov.hhs.cms.bluebutton.data.model.rif.RifRecordEvent;
import gov.hhs.cms.bluebutton.data.model.rif.samples.StaticRifResource;

/**
 * Unit test for simple RifParser.
 */
public class RifParserTest {
	/**
	 * Test RIF file parsing
	 */
	@Test
	public void testRifFileStatic() {	
		RifParser parser = new RifParser(StaticRifResource.SAMPLE_A_BENES.toRifFile());
		RifRecordEvent<?> rifRecordEvent = parser.next();
		if(rifRecordEvent != null) {
			Beneficiary beneRow = (Beneficiary) rifRecordEvent.getRecord();
			Assert.assertEquals("567834", beneRow.getBeneficiaryId());
		}
	}
	
	@Test
	public void testRifFileLocal() {			
		try {
			RifParser parser  = new RifParser("beneficiary_truncated.rif", RifFileType.BENEFICIARY);
			RifRecordEvent<?> rifRecordEvent = parser.next();
			if(rifRecordEvent != null) {
				Beneficiary beneRow = (Beneficiary) rifRecordEvent.getRecord();
				Assert.assertEquals("1", beneRow.getBeneficiaryId());
			}
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
}
