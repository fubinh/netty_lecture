package cn.org.fubin.javavsnetty;

/**
 * Created by fubin on 2019-07-25.
 */
public class SystemTypeUtil {

    public static void main(String[] args) {
        String os = System.getProperty("os.name");
        if(os.toLowerCase().startsWith("win")){
            System.out.println(os + " can't gunzip");
        }

        System.out.println(os);
    }

}
