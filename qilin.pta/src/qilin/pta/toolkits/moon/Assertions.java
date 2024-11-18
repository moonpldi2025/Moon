package qilin.pta.toolkits.moon;


public class Assertions {
    public static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }

    public static void check(boolean condition, String failMessage) {
        if (!condition) {
            throw new AssertionError(failMessage);
        }
    }

    public static void check(boolean condition, String failMessage, Object... args) {
        if (!condition) {
            throw new AssertionError(String.format(failMessage, args));
        }
    }

    public static void panic(String failMessage) {
        check(false, failMessage);
    }

    public static void debug(){
        System.out.println("DEBUG");
    }

    public static void debug(String debugMessage){
        System.out.println("DEBUG: " + debugMessage);
    }
    public static void debug(Object o){
        System.out.println("DEBUG: " + o);
    }

    public static void todo(){
        panic("TODO");
    }
}
