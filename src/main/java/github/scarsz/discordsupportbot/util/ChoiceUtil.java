package github.scarsz.discordsupportbot.util;

public class ChoiceUtil {

    public static String character(int number) {
        switch (number) {
            case 1: return Emoji.REGIONAL_INDICATOR_A;
            case 2: return Emoji.REGIONAL_INDICATOR_B;
            case 3: return Emoji.REGIONAL_INDICATOR_C;
            case 4: return Emoji.REGIONAL_INDICATOR_D;
            case 5: return Emoji.REGIONAL_INDICATOR_E;
            case 6: return Emoji.REGIONAL_INDICATOR_F;
            case 7: return Emoji.REGIONAL_INDICATOR_G;
            case 8: return Emoji.REGIONAL_INDICATOR_H;
            case 9: return Emoji.REGIONAL_INDICATOR_I;
            case 10: return Emoji.REGIONAL_INDICATOR_J;
            case 11: return Emoji.REGIONAL_INDICATOR_K;
            case 12: return Emoji.REGIONAL_INDICATOR_L;
            case 13: return Emoji.REGIONAL_INDICATOR_M;
            case 14: return Emoji.REGIONAL_INDICATOR_N;
            case 15: return Emoji.REGIONAL_INDICATOR_O;
            case 16: return Emoji.REGIONAL_INDICATOR_P;
            case 17: return Emoji.REGIONAL_INDICATOR_Q;
            case 18: return Emoji.REGIONAL_INDICATOR_R;
            case 19: return Emoji.REGIONAL_INDICATOR_S;
            case 20: return Emoji.REGIONAL_INDICATOR_T;
            case 21: return Emoji.REGIONAL_INDICATOR_U;
            case 22: return Emoji.REGIONAL_INDICATOR_V;
            case 23: return Emoji.REGIONAL_INDICATOR_W;
            case 24: return Emoji.REGIONAL_INDICATOR_X;
            case 25: return Emoji.REGIONAL_INDICATOR_Y;
            case 26: return Emoji.REGIONAL_INDICATOR_Z;
            default: return null;
        }
    }

    public static String number(int number) {
        switch (number) {
            case 1: return Emoji.ONE;
            case 2: return Emoji.TWO;
            case 3: return Emoji.THREE;
            case 4: return Emoji.FOUR;
            case 5: return Emoji.FIVE;
            case 6: return Emoji.SIX;
            case 7: return Emoji.SEVEN;
            case 8: return Emoji.EIGHT;
            case 9: return Emoji.NINE;
            case 10: return Emoji.TEN;
            default: return Emoji.ZERO;
        }
    }

    public static int decimal(String emoji) {
        switch (emoji) {
            case Emoji.REGIONAL_INDICATOR_A: return 1;
            case Emoji.REGIONAL_INDICATOR_B: return 2;
            case Emoji.REGIONAL_INDICATOR_C: return 3;
            case Emoji.REGIONAL_INDICATOR_D: return 4;
            case Emoji.REGIONAL_INDICATOR_E: return 5;
            case Emoji.REGIONAL_INDICATOR_F: return 6;
            case Emoji.REGIONAL_INDICATOR_G: return 7;
            case Emoji.REGIONAL_INDICATOR_H: return 8;
            case Emoji.REGIONAL_INDICATOR_I: return 9;
            case Emoji.REGIONAL_INDICATOR_J: return 10;
            case Emoji.REGIONAL_INDICATOR_K: return 11;
            case Emoji.REGIONAL_INDICATOR_L: return 12;
            case Emoji.REGIONAL_INDICATOR_M: return 13;
            case Emoji.REGIONAL_INDICATOR_N: return 14;
            case Emoji.REGIONAL_INDICATOR_O: return 15;
            case Emoji.REGIONAL_INDICATOR_P: return 16;
            case Emoji.REGIONAL_INDICATOR_Q: return 17;
            case Emoji.REGIONAL_INDICATOR_R: return 18;
            case Emoji.REGIONAL_INDICATOR_S: return 19;
            case Emoji.REGIONAL_INDICATOR_T: return 20;
            case Emoji.REGIONAL_INDICATOR_U: return 21;
            case Emoji.REGIONAL_INDICATOR_V: return 22;
            case Emoji.REGIONAL_INDICATOR_W: return 23;
            case Emoji.REGIONAL_INDICATOR_X: return 24;
            case Emoji.REGIONAL_INDICATOR_Y: return 25;
            case Emoji.REGIONAL_INDICATOR_Z: return 26;
//            case Emoji.ZERO: return 0;
            case Emoji.ONE: return 1;
            case Emoji.TWO: return 2;
            case Emoji.THREE: return 3;
            case Emoji.FOUR: return 4;
            case Emoji.FIVE: return 5;
            case Emoji.SIX: return 6;
            case Emoji.SEVEN: return 7;
            case Emoji.EIGHT: return 8;
            case Emoji.NINE: return 9;
            case Emoji.TEN: return 10;
            default: return 0;
        }
    }

    public static boolean isValidChoice(String choice) {
        return decimal(choice) != 0;
    }

}
