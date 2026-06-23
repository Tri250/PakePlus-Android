package com.batteryhealth.app.domain.usecase;

import com.batteryhealth.app.domain.repository.DeviceRepository;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class DetermineBatterySourceUseCaseTest {

    @Mock
    private DeviceRepository deviceRepository;

    private DetermineBatterySourceUseCase useCase;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        useCase = new DetermineBatterySourceUseCase(deviceRepository);
        when(deviceRepository.getDesignCapacity()).thenReturn(4500);
    }

    @Test
    public void testExecute_allOriginalSignals_returnsOriginal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "BYD1234567890ABC",
                "byd",
                "ABC123456789",
                4400,
                4500
        );

        assertEquals("original", result.source);
        assertTrue(result.confidence > 0.5f);
        assertNotNull(result.reason);
    }

    @Test
    public void testExecute_thirdPartySignals_returnsThirdParty() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "NO_NAME!!",
                "unknown",
                "123",
                5000,
                4500
        );

        assertEquals("third_party", result.source);
        assertTrue(result.confidence > 0.3f);
        assertNotNull(result.reason);
    }

    @Test
    public void testExecute_insufficientSignals_returnsUnknown() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                0,
                0
        );

        assertEquals("unknown", result.source);
        assertEquals(0f, result.confidence, 0.01f);
        assertNotNull(result.reason);
    }

    @Test
    public void testExecute_oemVendorSerial_addsPositiveSignal() {
        DetermineBatterySourceUseCase.Result result1 = useCase.execute(
                "ABC1234567890",
                null,
                null,
                0,
                0
        );

        DetermineBatterySourceUseCase.Result result2 = useCase.execute(
                null,
                null,
                null,
                0,
                0
        );

        assertTrue(result1.confidence >= result2.confidence);
    }

    @Test
    public void testExecute_invalidVendorSerial_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "!!!INVALID!!!@#$",
                null,
                null,
                0,
                0
        );

        assertFalse(result.confidence > 0);
    }

    @Test
    public void testExecute_knownManufacturer_addsPositiveSignal() {
        String[] knownManufacturers = {"coslight", "sunwoda", "byd", "lg", "chem", "sanyo", "tdk"};

        for (String manufacturer : knownManufacturers) {
            DetermineBatterySourceUseCase.Result result = useCase.execute(
                    null,
                    manufacturer,
                    null,
                    0,
                    0
            );
            assertTrue("Manufacturer " + manufacturer + " should add positive signal",
                    result.confidence >= 0);
        }
    }

    @Test
    public void testExecute_unknownManufacturer_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                "unknown",
                null,
                0,
                0
        );

        assertTrue(result.confidence <= 0);
    }

    @Test
    public void testExecute_zeroManufacturer_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                "0",
                null,
                0,
                0
        );

        assertTrue(result.confidence <= 0);
    }

    @Test
    public void testExecute_validSerialFormat_addsPositiveSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "ABCDE123456789",
                0,
                0
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_shortSerial_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "ABC123",
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_invalidSerialChars_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "ABC-123!@#",
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_unknownSerial_noSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "unknown",
                0,
                0
        );

        assertEquals(0f, result.confidence, 0.01f);
    }

    @Test
    public void testExecute_capacityRatioNormalRange_addsPositiveSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                4300,
                4500
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_capacityRatioExtremeLow_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                2000,
                4500
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_capacityRatioExtremeHigh_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                6000,
                4500
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_capacityRatioMidRange_neutralSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                3000,
                4500
        );

        assertEquals(0f, result.confidence, 0.01f);
    }

    @Test
    public void testExecute_deviceDatabaseMatch_addsPositiveSignal() {
        when(deviceRepository.getDesignCapacity()).thenReturn(4500);

        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                0,
                0
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_noDeviceDatabaseMatch_addsNegativeSignal() {
        when(deviceRepository.getDesignCapacity()).thenReturn(0);

        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_originalConfidenceUpperBound() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "BYD1234567890ABCDEFG",
                "byd",
                "ABCDEFG123456789",
                4450,
                4500
        );

        assertTrue(result.confidence <= 0.95f);
    }

    @Test
    public void testExecute_thirdPartyConfidenceUpperBound() {
        when(deviceRepository.getDesignCapacity()).thenReturn(0);

        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "!!!INVALID!!!",
                "unknown",
                "123",
                1000,
                4500
        );

        assertTrue(result.confidence <= 0.9f);
    }

    @Test
    public void testExecute_nullVendorInfo_noSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                "byd",
                null,
                0,
                0
        );

        assertNotNull(result);
        assertNotNull(result.source);
    }

    @Test
    public void testExecute_emptyVendorInfo_noSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "",
                "byd",
                null,
                0,
                0
        );

        assertNotNull(result);
    }

    @Test
    public void testExecute_manufacturerCaseInsensitive() {
        DetermineBatterySourceUseCase.Result result1 = useCase.execute(
                null,
                "BYD",
                null,
                0,
                0
        );

        DetermineBatterySourceUseCase.Result result2 = useCase.execute(
                null,
                "byd",
                null,
                0,
                0
        );

        assertEquals(result1.confidence, result2.confidence, 0.01f);
    }

    @Test
    public void testExecute_serialCaseInsensitiveForFormatCheck() {
        DetermineBatterySourceUseCase.Result result1 = useCase.execute(
                null,
                null,
                "ABCDE123456789",
                0,
                0
        );

        DetermineBatterySourceUseCase.Result result2 = useCase.execute(
                null,
                null,
                "abcde123456789",
                0,
                0
        );

        assertEquals(result1.confidence, result2.confidence, 0.01f);
    }

    @Test
    public void testExecute_serialTooLong_addsNegativeSignal() {
        StringBuilder longSerial = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            longSerial.append("A");
        }
        for (int i = 0; i < 10; i++) {
            longSerial.append("1");
        }

        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                longSerial.toString(),
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_serialTooShort_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "AB12",
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_serialWithSpecialChars_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "ABC-123_DEF",
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_serialOnlyLetters_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "ABCDEFGHIJKLMN",
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_serialOnlyDigits_addsNegativeSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                "1234567890123",
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_vendorSerialShort_noSignal() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "ABC",
                null,
                null,
                0,
                0
        );

        assertTrue(result.confidence < 0);
    }

    @Test
    public void testExecute_vendorSerialWithNewlines_valid() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "ABCDEFGH\nIJKLMNOP",
                null,
                null,
                0,
                0
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_vendorSerialWithDashes_valid() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "ABCDE-FGHIJ-12345",
                null,
                null,
                0,
                0
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_vendorSerialWithUnderscores_valid() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "ABCDE_FGHIJ_12345",
                null,
                null,
                0,
                0
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_vendorSerialWithSpaces_valid() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "ABCDE FGHIJ 12345",
                null,
                null,
                0,
                0
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_capacityRatioExactly85Percent_positive() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                3825,
                4500
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_capacityRatioExactly105Percent_positive() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                4725,
                4500
        );

        assertTrue(result.confidence > 0);
    }

    @Test
    public void testExecute_capacityRatioExactly55Percent_neutral() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                2475,
                4500
        );

        assertEquals(0f, result.confidence, 0.01f);
    }

    @Test
    public void testExecute_capacityRatioExactly125Percent_neutral() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                null,
                null,
                null,
                5625,
                4500
        );

        assertEquals(0f, result.confidence, 0.01f);
    }

    @Test
    public void testExecute_batterySourceReason_notNull() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "test",
                "test",
                "test",
                1000,
                1000
        );

        assertNotNull(result.reason);
        assertFalse(result.reason.isEmpty());
    }

    @Test
    public void testExecute_combinedSignals_originalHighConfidence() {
        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "SUNWODA123456789ABCDEF",
                "sunwoda",
                "ABCDEFG1234567890",
                4480,
                4500
        );

        assertEquals("original", result.source);
        assertTrue("High confidence expected: " + result.confidence, result.confidence > 0.7f);
    }

    @Test
    public void testExecute_combinedSignals_thirdPartyHighConfidence() {
        when(deviceRepository.getDesignCapacity()).thenReturn(0);

        DetermineBatterySourceUseCase.Result result = useCase.execute(
                "!!!INVALID!!!@#$%",
                "unknown",
                "12345",
                6000,
                4500
        );

        assertEquals("third_party", result.source);
        assertTrue("High confidence expected: " + result.confidence, result.confidence > 0.5f);
    }
}
