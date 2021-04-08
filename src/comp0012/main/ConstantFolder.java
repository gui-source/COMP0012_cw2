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
		MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(),
                null, method.getName(), cgen.getClassName(), instList, cpgen);

		simple_int_folding(instList, cpgen, cp);

		// just keeping this here as a framework to work with
		for(InstructionHandle handle: instList.getInstructionHandles()){

			// for each instruction
			// if see an operation (bodmas) check if operands are constants:
			// if they are - calculate and replace


			// everytime variable is declared, add it to keep track
			// if variable is called in istore or dstore or sth store, note earliest call
			// do a second pass using these values and instead of loading variables, use iconst/dconst *value*
		}

		// making sure goto statements in instList are ok
		instList.setPositions(true);

		// neccessary stuff to do to methodGen
		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		// since methodGen has reference to instList and cpgen, it will create new method with optimizations done
		Method newMethod = methodGen.getMethod();

		// replace method
		cgen.replaceMethod(method, newMethod);
	}

	private void simple_int_folding(InstructionList instList, ConstantPoolGen cpgen, ConstantPool cp){
		// initialise InstructionFinder and regex
		InstructionFinder f = new InstructionFinder(instList);
		String pat ="(ICONST|BIPUSH|SIPUSH|LDC) (ICONST|BIPUSH|SIPUSH|LDC) (IMUL|IADD|IDIV|ISUB)";

		// standard for loop - iterate through i which contains results of search from f
		// while i.hasNext, we continue looping
		for(Iterator i = f.search(pat); i.hasNext();){

			// initialise match as an array of instructions
			// use instruction handle as easier way to deal with instructions this way
			// within match, first element is integer, second element integer, third element operator
			InstructionHandle[] match = (InstructionHandle[]) i.next();

//			// print statements to check instructions
//			for (InstructionHandle handle: match){
//				System.out.println(handle.toString());
//			}

			// extract values from match[0] and match[1]
			int c1;
			int c2;
			Instruction inst = match[0].getInstruction();
			if(inst instanceof LDC){
				// cast inst to be LDC so we can get index of the constant within the constant pool
				// then we call cp.getConstant(index) to get the Constant object representing integer
				// cast Constant object to a ConstantInteger to that we can use getBytes to extract value from it
				c1 = ((ConstantInteger)(cp.getConstant(((LDC) inst).getIndex()))).getBytes();
			}
			else{
				// for ICONST, BIPUSH, SIPUSH, we can cast it to an ICONST object and do getValue
				// need to cast to integer since getValue returns a Number object
				c1 = (int)(((ICONST)inst).getValue());
			}
			inst = match[1].getInstruction();
			if(inst instanceof LDC){
				c2 = ((ConstantInteger)(cp.getConstant(((LDC) inst).getIndex()))).getBytes();
			}
			else{
				c2 = (int)(((ICONST)inst).getValue());
			}

			// result doesn't need to be initialised tbh, intellij just super picky
			// result is value that we are folding
			int result = 0;

			// just a bunch of switch cases to know which operation to do
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

			// here toWrite is Instruction that we shall replace the (int) (int) (operator) instructions with
			Instruction toWrite;

			// if else statements to decide which instruction to use based on value ranges
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

			// insert toWrite before first Instruction in match
			instList.insert(match[0],toWrite);
			debug = true;

			// delete all Instructions in match
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


	//float simple folding operations following int folding structure
    private void simple_float_folding(InstructionList instList, ConstantPoolGen cpgen, ConstantPool cp){
        InstructionFinder f = new InstructionFinder(instList);
        String pat ="(FCONST|LDC|LDC_W) (FCONST|LDC|LDC_W) (FMUL|FADD|FDIV|FSUB)";

        for (Iterator i = f.search(pat); i.hasNext();){
            InstructionHandle[] match = (InstructionHandle[]) i.next();
            float c1, c2;

            Instruction inst = match[0].getInstruction();
            if(inst instanceof LDC){
                c1 = ((ConstantFloat)(cp.getConstant(((LDC) inst).getIndex()))).getBytes();
            }
            else{
                c1 = (int)(((FCONST)inst).getValue());
            }
            inst = match[1].getInstruction();
            if(inst instanceof LDC){
                c2 = ((ConstantFloat)(cp.getConstant(((LDC) inst).getIndex()))).getBytes();
            }
            else{
                c2 = (float)(((FCONST)inst).getValue());
            }


            float result = 0;


            switch (match[2].getInstruction().getName())
            {
                case "fmul":
                    result = c1*c2;
                    break;
                case "fadd":
                    result = c1+c2;
                    break;
                case "fsub":
                    result = c1-c2;
                    break;
                case "fdiv":
                    result = c1/c2;
                    break;
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

		// for each method, we run optimizeMethod
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