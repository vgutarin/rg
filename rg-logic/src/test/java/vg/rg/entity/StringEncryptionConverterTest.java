package vg.rg.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.rg.service.EncryptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class StringEncryptionConverterTest {

    @Mock
    EncryptionService encryptionService;

    @InjectMocks
    StringEncryptionConverter converter;

    @Test
    void convertToDatabaseColumn_whenValueIsProvided_delegatesToEncryptionService() {
        var plaintext = nextString();
        var encoded = new byte[]{1, 2, 3};
        when(encryptionService.encode(plaintext)).thenReturn(encoded);

        assertThat(converter.convertToDatabaseColumn(plaintext)).isEqualTo(encoded);
    }

    @Test
    void convertToEntityAttribute_whenValueIsProvided_delegatesToEncryptionService() {
        var encoded = new byte[]{1, 2, 3};
        var plaintext = nextString();
        when(encryptionService.decode(encoded)).thenReturn(plaintext);

        assertThat(converter.convertToEntityAttribute(encoded)).isEqualTo(plaintext);
    }

    @Test
    void convertToDatabaseColumn_whenInputIsNull_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();

        verify(encryptionService, never()).encode(null);
    }

    @Test
    void convertToEntityAttribute_whenInputIsNull_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();

        verify(encryptionService, never()).decode(null);
    }
}
