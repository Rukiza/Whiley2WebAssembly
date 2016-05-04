package testing;

import com.eclipsesource.v8.V8;
import com.sun.tools.javac.util.List;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import util.WastFactory;
import wycc.util.Pair;
import wyil.io.WyilFileReader;
import wyil.lang.WyilFile;
import wywasm.WasmFileWriter;

import java.io.*;
import java.util.Collection;
import java.util.Map;

import static junit.framework.TestCase.fail;

/**
 * Created by Shane on 8/04/16.
 */
@RunWith(Parameterized.class)
public class ValidTests {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return TestUtils.findTestNames("tests/valid/");
    }

    private String testName;

    public ValidTests (String testName) {
        this.testName = testName;
    }

    @Test
    public void test () {
//        try {

        Pair<Integer, String> val = TestUtils.compile("-wd", "tests/valid/","tests/valid/"+testName+".whiley");

//        System.out.println(val);
        if (val.first() != 0) {
            System.err.println(TestUtils.compile("-version"));
            fail(val.toString());
        }
        //WasmFileWriter.main(new String[]{"tests/valid/"+testName+".wyil"});
        try {
            // First, read the input file to generate WyilFile instance
            WyilFile file = new WyilFileReader("tests/valid/"+testName+".wyil").read();
            // Second, pass WyilFile into wasm file writer
            new WasmFileWriter(System.out, new WastFactory.SWastFactory()).write(file);
        } catch (IOException e) {
            e.printStackTrace();
            fail("wyil file missing.");
        }

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("wasm/test2.wast", true));
            bw.write("\n( invoke \"test\" )");
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String wasmVal = TestUtils.execWast("../../WebAssembly/spec/ml-proto/", "wasm", "test2.wast");

        if (wasmVal == null) {
            fail("Test failed to interpret");
        } else {
            System.out.println(wasmVal);
        }

//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

}
