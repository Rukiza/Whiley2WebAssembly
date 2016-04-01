package wywasm;

import java.io.IOException;
import java.io.PrintStream;

import wyil.io.WyilFilePrinter;
import wyil.io.WyilFileReader;
import wyil.lang.*;

public class WasmFileWriter {
	private PrintStream output;
	
	public WasmFileWriter(PrintStream output) {
		this.output = output;
	}
	
	public void write(WyilFile file) throws IOException {		
		for(WyilFile.FunctionOrMethod d : file.functionOrMethods()) {
			write(d);			
		}
	}
	
	/**
	 * Translate a function or method into WebAssembly
	 * 
	 * @param d
	 * @throws IOException 
	 */
	private void write(WyilFile.FunctionOrMethod d) throws IOException {
		// FIXME: This is only an illustration. It's not generating valid
		// WebAssembly!!
		
		output.print(d.name());
		output.print(" ");
		output.println(d.type());
		if(d.body() != null) {			
			write(d.body(),4);
		}
	}
		
	private void write(CodeBlock c, int indent) {
		for(Code bytecode : c.bytecodes()) {
			indent(indent);
			if(bytecode instanceof Code.Compound) {
				output.println("\t" + bytecode.getClass().getName() + " {");
				write((Code.Compound) bytecode,indent+4);
				output.println("}");
			} else {
				output.println("\t" + bytecode);
			}
		}
	}
	
	private void indent(int indent) {
		for(int i=0;i!=indent;++i) {
			output.print(" ");
		}
	}
	
	public static void main(String[] args) {
		try {
			// First, read the input file to generate WyilFile instance
			WyilFile file = new WyilFileReader(args[0]).read();
			// Second, pass WyilFile into wasm file writer
			new WasmFileWriter(System.out).write(file);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
