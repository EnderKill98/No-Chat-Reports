package com.aizistral.nochatreports.encryption;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.aizistral.nochatreports.NoChatReports;
import it.unimi.dsi.fastutil.chars.Char2CharArrayMap;
import it.unimi.dsi.fastutil.chars.Char2CharMap;
import it.unimi.dsi.fastutil.chars.Char2CharMaps;

import static java.util.Map.entry;

public abstract class Encryptor<T extends Encryption> {
	protected static final SecureRandom RANDOM = new SecureRandom();
	protected static final Char2CharMap BASE64R_SHIFTS = createBase64RShifts();
	protected static final Char2CharMap BASE64R_SHIFTS_REVERSE = createBase64RShiftsReverse();
	protected static final Char2CharMap SUS16_SHIFTS = createSus16Shifts();
	protected static final Char2CharMap SUS16_SHIFTS_REVERSE = createSus16ShiftsReverse();

	private static String MC256_SHIFTS = "⅛⅜⅝⅞⅓⅔✉☂☔☄⛄☃⚐✎❣♤♧♡♢⛈ªº¬«»░▒▓∅∈≡±≥≤⌠⌡÷≈°∙√ⁿ²¡‰­·₴≠×ΦΨικλοπτυφЯабвгдежзиклмнопрстуфхцчшщъыьэюяєѕіј„…⁊←↑→↓⇄＋ƏəɛɪҮүӨөʻˌ;ĸ⁰¹³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁱ™⧈⚔☠ᴀʙᴄᴅᴇꜰɢʜᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡʏᴢ¢¤¥©®µ¶¼½¾·‐‚†‡•‱′″‴‵‶‷‹›※‼⁂⁉⁎⁑⁒⁗℗−∓∞☀☁☈Є☲☵☽♀♂⚥♠♣♥♦♩♪♫♬♭♮♯⚀⚁⚂⚃⚄⚅ʬ⚡⛏✔❄❌❤⭐△▷▽◁◆◇○◎☆★✘⸸▲▶▼◀●◦◘⚓ᛩᛪ☺☻";
	private static char INVIS2_ZERO = ' '; // Space
	private static char INVIS2_ONE = '\u200c';

	protected Encryptor() {
		// NO-OP
	}

	public abstract String encrypt(String message);

	public abstract String decrypt(String message);

	public abstract T getAlgorithm();

	public abstract String getKey();

	protected static String shiftBase64R(String string) {
		char[] chars = ensureUTF8(string).toCharArray();

		for (int i = 0; i < chars.length; i++) {
			chars[i] = BASE64R_SHIFTS.get(chars[i]);
		}

		return new String(chars);
	}

	protected static String unshiftBase64R(String string) {
		char[] chars = ensureUTF8(string).toCharArray();

		for (int i = 0; i < chars.length; i++) {
			chars[i] = BASE64R_SHIFTS_REVERSE.get(chars[i]);
		}

		return new String(chars);
	}


	protected static String shiftSus16(String string) {
		char[] chars = ensureUTF8(string).toCharArray();

		for (int i = 0; i < chars.length; i++) {
			chars[i] = SUS16_SHIFTS.get(chars[i]);
		}

		return new String(chars);
	}

	protected static String unshiftSus16(String string) {
		char[] chars = ensureUTF8(string).toCharArray();

		for (int i = 0; i < chars.length; i++) {
			chars[i] = SUS16_SHIFTS_REVERSE.get(chars[i]);
		}

		return new String(chars);
	}

	protected static byte[] toBytes(String string) {
		return string.getBytes(StandardCharsets.UTF_8);
	}

	protected static String fromBytes(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8);
	}

	protected static String ensureUTF8(String string) {
		return fromBytes(toBytes(string));
	}

	protected static String encodeBase64(String message) {
		return encodeBase64(toBytes(message));
	}

	protected static String encodeBase64(byte[] bytes) {
		return fromBytes(Encryption.BASE64_ENCODER.encode(bytes)).replace('/', '\\');
	}


	protected static String encodeSus(String string) {
		return encodeSus16(toBytes(string));
	}

	protected static String encodeSus16(byte[] bytes) {
		return shiftSus16(fromBytes(Encryption.BASE16.encode(bytes)));
	}

	protected static byte[] encodeSus16Bytes(String string) {
		return toBytes(encodeSus16(toBytes(string)));
	}

	protected static String encodeBase64R(String string) {
		return encodeBase64R(toBytes(string));
	}

	protected static String encodeBase64R(byte[] bytes) {
		return shiftBase64R(fromBytes(Encryption.BASE64_ENCODER.encode(bytes)));
	}

	protected static byte[] encodeBase64RBytes(String string) {
		return toBytes(encodeBase64R(toBytes(string)));
	}

	protected static String decodeBase64R(String string) {
		return fromBytes(decodeBase64RBytes(string));
	}

	protected static String decodeBase64(String string) {
		return fromBytes(decodeBase64NonRBytes(string));
	}

	protected static byte[] decodeBase64RBytes(String string) {
		return Encryption.BASE64_DECODER.decode(toBytes(unshiftBase64R(string)));
	}

	protected static byte[] decodeBase64Bytes(String string) {
		return Encryption.BASE64_DECODER.decode(toBytes(unshiftBase64R(string)));
	}

	protected static byte[] decodeSus16Bytes(String string) {
		return Encryption.BASE16.decode(toBytes(unshiftSus16(string)));
	}

	protected static String encodeMC256(byte[] data) {
		String res = new String();
		for (int i = 0; i < data.length; ++i) {
			res = res + MC256_SHIFTS.charAt(data[i]&0xff);
		}
		return res;
	}

	protected static byte[] decodeMC256(String string) {
		byte[] bytes = new byte[string.length()];
		for (int i = 0; i < string.length(); ++i) {
			bytes[i] = (byte)MC256_SHIFTS.indexOf(string.charAt(i));
		}
		return bytes;
	}

	protected static String encodeInvis2(byte[] data) {
		StringBuilder encoded = new StringBuilder();
		for(byte b : data) {
			for (int bit = 7; bit >= 0; bit--) {
				encoded.append(((b >>> bit) & 0x01) == 1 ? INVIS2_ONE : INVIS2_ZERO);
			}
		}
		return encoded.toString();
	}

	protected static byte[] decodeInvis2(String string) {
		ByteArrayOutputStream bout = new ByteArrayOutputStream(string.length() / 8);
		byte b = 0;
		int bit = 8;
		for(char c : string.toCharArray()) {
			if(c == INVIS2_ONE) {
				bit--;
				b |= 0x01 << bit;
			}else if(c == INVIS2_ZERO) {
				bit--;
			}else {
				continue;
			}

			// Got 8 bits add finished byte
			if(bit == 0) {
				bout.write(new byte[] { b }, 0, 1);
				bit = 8;
				b = 0;
			}
		}
		return bout.toByteArray();
	}

	protected static byte[] decodeBase64NonRBytes(String string) {
		return Encryption.BASE64_DECODER.decode(toBytes(string.replace("\\", "/")));
	}

	protected static String encodeBinaryKey(byte[] key) {
		return fromBytes(Encryption.BASE64_ENCODER.encode(key));
	}

	protected static byte[] decodeBinaryKey(String key) throws InvalidKeyException {
		try {
			return Encryption.BASE64_DECODER.decode(toBytes(key));
		} catch (Exception ex) {
			throw new InvalidKeyException(ex);
		}
	}

	protected static byte[] mergeBytes(byte[] array1, byte[] array2) {
		byte[] result = Arrays.copyOf(array1, array1.length + array2.length);
		System.arraycopy(array2, 0, result, array1.length, array2.length);
		return result;
	}

	private static Char2CharMap createBase64RShiftsReverse() {
		Char2CharMap map = createBase64RShifts();
		Char2CharMap reverse = new Char2CharArrayMap(64);

		for (char ch : map.keySet()) {
			reverse.put(map.get(ch), ch);
		}

		return Char2CharMaps.unmodifiable(reverse);
	}


	private static Char2CharMap createSus16ShiftsReverse() {
		Char2CharMap map = createSus16Shifts();
		Char2CharMap reverse = new Char2CharArrayMap(64);

		for (char ch : map.keySet()) {
			reverse.put(map.get(ch), ch);
		}

		return Char2CharMaps.unmodifiable(reverse);
	}

	private static Char2CharMap createBase64RShifts() {
		Char2CharMap map = new Char2CharArrayMap(64);

		map.put('A', '!');
		map.put('B', '"');
		map.put('C', '#');
		map.put('D', '$');
		map.put('E', '%');
		map.put('F', '¼');
		map.put('G', '\'');
		map.put('H', '(');
		map.put('I', ')');
		map.put('J', ',');
		map.put('K', '-');
		map.put('L', '.');
		map.put('M', ':');
		map.put('N', ';');
		map.put('O', '<');
		map.put('P', '=');
		map.put('Q', '>');
		map.put('R', '?');
		map.put('S', '@');
		map.put('T', '[');
		map.put('U', '\\');
		map.put('V', ']');
		map.put('W', '^');
		map.put('X', '_');
		map.put('Y', '`');
		map.put('Z', '{');
		map.put('a', '|');
		map.put('b', '}');
		map.put('c', '~');
		map.put('d', '¡');
		map.put('e', '¢');
		map.put('f', '£');
		map.put('g', '¤');
		map.put('h', '¥');
		map.put('i', '¦');
		map.put('j', '¨');
		map.put('k', '©');
		map.put('l', 'ª');
		map.put('m', '«');
		map.put('n', '¬');
		map.put('o', '®');
		map.put('p', '¯');
		map.put('q', '°');
		map.put('r', '±');
		map.put('s', '²');
		map.put('t', '³');
		map.put('u', 'µ');
		map.put('v', '¶');
		map.put('w', '·');
		map.put('x', '¸');
		map.put('y', '¹');
		map.put('z', 'º');
		map.put('0', '0');
		map.put('1', '1');
		map.put('2', '2');
		map.put('3', '3');
		map.put('4', '4');
		map.put('5', '5');
		map.put('6', '6');
		map.put('7', '7');
		map.put('8', '8');
		map.put('9', '9');
		map.put('+', '+');
		map.put('/', '»');
		map.put('=', '¿');

		return Char2CharMaps.unmodifiable(map);
	}

	private static Char2CharMap createSus16Shifts() {
		Char2CharMap map = new Char2CharArrayMap(64);

		String source = "0123456789ABCDEF";
		String target = "ඔඕඖඞචඩඬධඹවဨ၅၆၉ၡဥ";
		if(source.length() != target.length()) {
			throw new RuntimeException("Source and target mapping string are of different length!");
		}
		for(int i = 0; i < source.length(); i++) {
			map.put(source.charAt(i), target.charAt(i));
			if(target.substring(0, i).contains("" + target.charAt(i))) {
				throw new RuntimeException("Found duplicate characters in target mapping!");
			}
		}
		return Char2CharMaps.unmodifiable(map);
	}

}
