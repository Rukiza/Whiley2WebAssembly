package testing;

import com.eclipsesource.v8.V8;
import org.junit.Test;
import util.WastFactory;
import wyil.io.WyilFileReader;
import wyil.lang.WyilFile;
import wywasm.WasmFileWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**TODO: Make a better class name.
 * Created by Shane on 8/04/16.
 */
public class Tests {

    private static final String D8_LOCATION = "../../v8/out/native/d8";

    @Test
    public void testSimpleFunction () {
        try {
//            V8 v8Runtime = V8.createV8Runtime("--expose-wasm");
            WyilFile file = new WyilFileReader("tests/test1.wyil").read();
            new WasmFileWriter(System.out, new WastFactory.SWastFactory()).write(file);
            StringBuilder builder = new StringBuilder();
            Runtime.getRuntime().exec("../sexpr-wasm-prototype/out/sexpr-wasm test.wast -o test.wasm");
            Process process = Runtime.getRuntime().exec(D8_LOCATION+" --expose-wasm");

            System.out.println(process.isAlive());
            OutputStream stream = process.getOutputStream();
//            stream.write("var buffer = readbuffer(wasm/test.wasm);".getBytes());

            System.out.println(process.isAlive());

            InputStream inputStream = process.getInputStream();

//            builder.append("1+1");
//                    .append("var buffer = readbuffer(wasm/test.wasm);\n")
//                    .append("var module = Wast.instantiateModule(buffer, {});\n")
//                    .append("addOne = module.exports['addOne'];\n")
//                    .append("addOne(1);");

//            int result = v8Runtime.executeIntegerScript(builder.toString());
//            System.out.print("Answer: "+result);

            ProcessBuilder some_file = new ProcessBuilder();
            Map<String, String> env = some_file.environment();



        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
