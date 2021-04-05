package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.ICONST;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.TargetLostException;



public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		System.out.println(classFilePath);
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	public void optimizeMethod(){
		for(Method method: original.getMethods()){
			// for each method
			Code methodCode = method.getCode();
			InstructionList instList = new InstructionList(methodCode.getCode());
			for(InstructionHandle handle: instList.getInstructionHandles()){
				// for each instruction
				// if see an operation (bodmas) check if operands are constants:
				// if they are - calculate and replace

				// everytime variable is declared, add it to keep track
				// if variable is called in istore or dstore or sth store, note earliest call
				// do a second pass using these values and instead of loading variables, use iconst/dconst *value*
			}
		}
	}

	public void display()
	{
//		System.out.println("**************Constant Pool*****************");
//		System.out.println(original.getConstantPool());
//		System.out.println("*******Fields*********");
//		System.out.println(Arrays.toString(original.getFields()));
//		System.out.println();

		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		System.out.println("*******Methods*********");
		System.out.println(Arrays.toString(original.getMethods()));

		for(Method method:original.getMethods()){
			System.out.print("method: ");
			System.out.println(method);
			Code methodCode = method.getCode();
			InstructionList instList = new InstructionList(methodCode.getCode());
			for(InstructionHandle handle: instList.getInstructionHandles())
			{
				System.out.println(handle.toString());
				Instruction inst = handle.getInstruction();
				System.out.println(handle.getPosition());
				System.out.println(inst);
				//instruction instanceof mnemonic
			}
		}
	}
	
	public void optimize()
	{
		System.out.println(" in optimize ");
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		// Implement your optimization here
		display();
//		optimizeMethod();
        
		this.optimized = gen.getJavaClass();
	}

	
	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}