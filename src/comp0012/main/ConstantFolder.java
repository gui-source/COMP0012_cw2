package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.generic.Visitor;
import org.apache.bcel.util.InstructionFinder;


public class ConstantFolder
{
	boolean debug = false;
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

	public void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method){
		//get constants
		ConstantPool cp = cpgen.getConstantPool();

		//bytecode array
		Code methodCode = method.getCode();
		InstructionList instList = new InstructionList(methodCode.getCode());

		// Initialise a method generator with the original method as the baseline
		MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), instList, cpgen);

		simple_int_folding(instList, cpgen, cp);

		for(InstructionHandle handle: instList.getInstructionHandles()){
			// for each instruction
			// if see an operation (bodmas) check if operands are constants:
			// if they are - calculate and replace


			// everytime variable is declared, add it to keep track
			// if variable is called in istore or dstore or sth store, note earliest call
			// do a second pass using these values and instead of loading variables, use iconst/dconst *value*
		}


		instList.setPositions(true);

		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		Method newMethod = methodGen.getMethod();

		cgen.replaceMethod(method, newMethod);
	}

	private void simple_int_folding(InstructionList instList, ConstantPoolGen cpgen, ConstantPool cp){
		InstructionFinder f = new InstructionFinder(instList);
		String pat ="(ICONST|BIPUSH|SIPUSH|LDC) (ICONST|BIPUSH|SIPUSH|LDC) (IMUL|IADD|IDIV|ISUB)";
		for(Iterator i = f.search(pat); i.hasNext();){
			InstructionHandle[] match = (InstructionHandle[]) i.next();
//			for (InstructionHandle handle: match){
//				System.out.println(handle.toString());
//			}
			int c1;
			int c2;
			Instruction inst = match[0].getInstruction();
			if(inst instanceof LDC){
				c1 = ((ConstantInteger)(cp.getConstant(((LDC) inst).getIndex()))).getBytes();
			}
			else{
				c1 = (int)(((ICONST)inst).getValue());
			}
			inst = match[1].getInstruction();
			if(inst instanceof LDC){
				c2 = ((ConstantInteger)(cp.getConstant(((LDC) inst).getIndex()))).getBytes();
			}
			else{
				c2 = (int)(((ICONST)inst).getValue());
			}
			int result = 0;
			switch (match[2].getInstruction().getName())
			{
				case "imul":
					result = c1*c2;
					break;
				case "iadd":
					result = c1+c2;
					break;
				case "isub":
					result = c1-c2;
					break;
				case "idiv":
					result = c1/c2;
					break;
			}
			Instruction toWrite;
			if(-1 <= result && result <= 5){
				toWrite = new ICONST(result);
			}
			else if (-128 <= result && result <= 127){
				toWrite = new BIPUSH((byte)result);
			}
			else if (-32768 <= result && result <= 32767){
				toWrite = new SIPUSH((short)result);
			}
			else{
				// add new constant to constant pool
				int index = cpgen.addInteger(result);
				toWrite = new LDC(index);
			}
			instList.insert(match[0],toWrite);
			debug = true;
			for (InstructionHandle handle :
					match) {
				try {
					instList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void display(Method method)
	{
//		System.out.println("**************Constant Pool*****************");
//		System.out.println(original.getConstantPool());
//		System.out.println("*******Fields*********");
//		System.out.println(Arrays.toString(original.getFields()));
//		System.out.println();
//
//		System.out.println("*******Methods*********");
//		System.out.println(Arrays.toString(original.getMethods()));

		System.out.print("method: ");
		System.out.println(method);
		Code methodCode = method.getCode();
		InstructionList instList = new InstructionList(methodCode.getCode());
		for(InstructionHandle handle: instList.getInstructionHandles())
		{
			System.out.println(handle.toString());
//			Instruction inst = handle.getInstruction();
//			System.out.println(handle.getPosition());
//			System.out.println(inst);
			//instruction instanceof mnemonic
		}
	}
	
	public void optimize()
	{
		ConstantPoolGen cpgen = gen.getConstantPool();
		// Implement your optimization here
		//display();
		for (Method method :
				gen.getMethods()) {
			optimizeMethod(gen, cpgen, method);
		}
        
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