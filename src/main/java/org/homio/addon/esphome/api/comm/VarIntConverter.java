package org.homio.addon.esphome.api.comm;

/**
 * Helper utility for converting integers to and from varints
 *
 * @author Arne Seime - Initial contribution
 */
public class VarIntConverter {

    /**
     * Convert an integer to a varint byte array
     */
    public static byte[] intToBytes(int value) {
        if (value <= 0x7F) {
            return new byte[]{(byte) value};
        }

        byte[] ret = new byte[10];
        int index = 0;

        while (value != 0) {
            byte temp = (byte) (value & 0x7F);
            value >>= 7;
            if (value != 0) {
                temp |= (byte) 0x80;
            }
            ret[index] = temp;
            index++;
        }

        return trimArray(ret, index);
    }

    /**
     * Convert a varint byte array to an integer
     */
    public static Integer bytesToInt(byte[] value) {
        int result = 0;
        int bitpos = 0;

        for (byte val : value) {
            result |= (val & 0x7F) << bitpos;
            if ((val & 0x80) == 0) {
                return result;
            }
            bitpos += 7;
        }

        return null;
    }

    private static byte[] trimArray(byte[] array, int length) {
        byte[] trimmedArray = new byte[length];
        System.arraycopy(array, 0, trimmedArray, 0, length);
        return trimmedArray;
    }
}
