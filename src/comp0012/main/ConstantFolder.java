package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;
import org.w3c.dom.Attr;


public class ConstantFolder
{
	boolean changed = false;
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	// if something changes, set changed to true then observe how to fix goto statements

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
		//bytecode array
		Code methodCode = method.getCode();
		InstructionList instList = new InstructionList(methodCode.getCode());

		// Initialise a method generator with the original method as the baseline
		MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(),
				null, method.getName(), cgen.getClassName(), instList, cpgen);



		// these arraylists keep track of the variables that never get reassigned and can be folded
		ArrayList<Integer> intConstVars = getConstantIntVars(instList);
		ArrayList<Integer> longConstVars = getConstantLongVars(instList);
		ArrayList<Integer> doubleConstVars = getConstantDoubleVars(instList);
		ArrayList<Integer> floatConstVars = getConstantFloatVars(instList);
		// these functions use those arraylists
		constantIntFolding(instList, cpgen, intConstVars);
		constantLongFolding(instList, cpgen, longConstVars);
		constantDoubleFolding(instList, cpgen, doubleConstVars);
		constantFloatFolding(instList, cpgen, floatConstVars);
		//checks for (integer) (integer to float/double/long) and replaces with corresponding float/double/long instruction
		convertFromInt(instList, cpgen);
		// the rest are same, just for long, float, double
		convertFromLong(instList, cpgen);
		convertFromFloat(instList, cpgen);
		convertFromDouble(instList, cpgen);
		// do simple folding last, since if any constant folding is done, simple folding will use those replaced constants
		simpleIntFolding(instList, cpgen);
		simpleLongFolding(instList, cpgen);
		simpleFloatFolding(instList, cpgen);
		simpleDoubleFolding(instList, cpgen);
		//
		dynamicIntFolding(instList, cpgen);

		instList.setPositions(true);

		// neccessary stuff to do to methodGen
		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		// since methodGen has reference to instList and cpgen, it will create new method with optimizations done
		Method newMethod = methodGen.getMethod();


		Attribute[] attributes = methodCode.getAttributes();
		System.out.println("FOR METHODCODE: ");
		System.out.println("+++++++++++++++++++");
		System.out.println(methodCode.toString());
		for (Attribute a :
				attributes) {
			System.out.println("======================");
			System.out.println("attribute: ");
			System.out.println(a.toString());
			System.out.println(a.getClass());
			if(a instanceof StackMapTable){
				StackMapTableEntry[] sm = ((StackMapTable) a).getStackMapTable();
				for (StackMapTableEntry smte :
						sm) {
					System.out.println("smte is: ");
					System.out.println(smte.toString());
				}
				System.out.println("NEW METHODCODE IS :");
				System.out.println(newMethod.getCode().toString());
				insertStackMapTable(newMethod, (StackMapTable) a);
			}
			System.out.println("=======================");
		}
//		}
//		System.out.println("FOR NEWMETHODCODE: ");
//		System.out.println(newMethod.toString());
//		System.out.println(newMethod.getCode().toString());
//		attributes = newMethod.getCode().getAttributes();
//		for (Attribute a :
//				attributes) {
//			System.out.println("attribute: ");
//			System.out.println(a.toString());
//			if(a instanceof Code){
//				Attribute[] aNested = ((Code) a).getAttributes();
//				for (Attribute att :
//						aNested) {
//					System.out.println("code has attribute: ");
//					System.out.println(att.toString());
//					if (att instanceof StackMap){
//						System.out.println("this above is a stackmaptable");
//					}
//				}
//			}
//		}

		// replace method
		cgen.replaceMethod(method, newMethod);

		//keep repeating if any changes were made - the repeated optimisation bit happens here
		if(changed){
			changed = false;
			optimizeMethod(cgen, cpgen, newMethod);
		}
	}

	private void insertStackMapTable(Method m, StackMapTable sm){
		StackMapTableEntry[] smte = sm.getStackMapTable();
		System.out.println("EEEEEEEEEEEEEEEEEEEEEEEEEEEE");
		for (StackMapTableEntry entry :
				smte) {
			System.out.println("entry is: ");
			System.out.println(entry.toString());
		}

		Attribute[] attributes = m.getAttributes();

		Code methodCode = m.getCode();
		InstructionList instList = new InstructionList(methodCode.getCode());

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(BranchInstruction)";

		int smteIndex = 0;
		Integer offset = null;
		Hashtable<Integer, BranchInstruction> posInstPair = new Hashtable<>();
		ArrayList<Integer> pos = new ArrayList<>();
		for (Iterator i = f.search(pat); i.hasNext() ; ) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int targetIndex = ((BranchInstruction) match[0].getInstruction()).getTarget().getPosition();
			pos.add(targetIndex);
		}
		Collections.sort(pos);
		for (int i = 0; i < pos.size(); i++)
		{
			int targetIndex = pos.get(i);
			System.out.println("target index is : " + targetIndex);
			System.out.println("from ");
			System.out.println(smte[smteIndex].toString());
			if(offset == null) {
				offset = targetIndex;
			}
			else{
				offset = targetIndex - 1 - offset;
			}
			smte[smteIndex].setByteCodeOffsetDelta(offset);
			System.out.println("to ");
			System.out.println(smte[smteIndex].toString());
			offset = targetIndex;
			smteIndex += 1;
		}
		for (StackMapTableEntry entry :
				smte) {
			System.out.println("entry is: ");
			System.out.println(entry.toString());
		}
		System.out.println("EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");

		for (Attribute a :
				attributes) {
			if(a instanceof Code){
				((Code) a).setAttributes(new Attribute[] {sm});
				System.out.println("success!");
			}
		}
	}

	private void replaceInst(InstructionHandle[] toReplace, Instruction replacement, InstructionList instList){
		// solves a weird problem that causes exception if instruction to be deleted is referenced by a goto statement
		// this just takes the references and puts it onto the new replacement instruction

		for (InstructionHandle handle :
				toReplace){
			// find out how to update the stackmaptable accordingly everytime we replaceInst
			if (handle != toReplace[0] && handle.hasTargeters()) {
				InstructionTargeter[] instTargs = handle.getTargeters();
				for (InstructionTargeter it :
						instTargs) {
					System.out.println("a it");
					System.out.println(it.toString());
					toReplace[0].addTargeter(it);

				}
				handle.removeAllTargeters();
			}
		}
		toReplace[0].setInstruction(replacement);
		for (InstructionHandle handle :
				toReplace) {
			if (handle == toReplace[0]) {
				continue;
			}
			else {
				try {
					instList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
				instList.setPositions();
			}
		}
//		instList.insert(toReplace[0], replacement);
//		for (InstructionHandle handle :
//				toReplace) {
//			try {
//				instList.delete(handle);
//			} catch (TargetLostException e) {
//				System.out.println("target lost");
//				e.printStackTrace();
//			}
//		}
		instList.setPositions(true);
		changed = true;
	}

	private void replaceInst(InstructionHandle toReplace, Instruction replacement, InstructionList instList){
		// for 1 to 1 replacement of instruction
//		instList.insert(toReplace, replacement);
//		try {
//			instList.delete(toReplace);
//		} catch (TargetLostException e) {
//			e.printStackTrace();
//		}
		toReplace.setInstruction(replacement);
		instList.setPositions(true);
		changed = true;
	}

	private void convertFromInt(InstructionList instList, ConstantPoolGen cpgen){
		// looks for integer constants, and i2f/i2d/i2l, replaces these two with a single float/double/long constant
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(ICONST|BIPUSH|SIPUSH|LDC) (I2F|I2D|I2L)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int original = getInt(match[0].getInstruction(), cpgen);
			Instruction conversion = match[1].getInstruction();
			Instruction inst;
			if (conversion instanceof  I2F) {
				inst = floatConstInst((float)original, cpgen);
			}
			else if (conversion instanceof  I2L) {
				inst = longConstInst( original, cpgen);
			}
			else {
				inst = doubleConstInst( original, cpgen);
			}

//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private void convertFromLong(InstructionList instList, ConstantPoolGen cpgen){
		// looks for long constant and conversion, replaces with corresponding constant type
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LCONST|LDC2_W) (L2F|L2I|L2D)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			long original = getLong(match[0].getInstruction(), cpgen);
			Instruction conversion = match[1].getInstruction();
			Instruction inst;
			if (conversion instanceof  L2F) {
				inst = floatConstInst((float)original, cpgen);
			}
			else if (conversion instanceof  L2I) {
				inst = intConstInst((int) original, cpgen);
			}
			else {
				inst = doubleConstInst((double) original, cpgen);
			}

//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private void convertFromFloat(InstructionList instList, ConstantPoolGen cpgen){
		// looks for float constant and conversion, replaces with corresponding constant type
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(FCONST|LDC_W) (F2I|F2D|F2L)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			float original = getFloat(match[0].getInstruction(), cpgen);
			Instruction conversion = match[1].getInstruction();
			Instruction inst;
			if (conversion instanceof F2I){
				inst = intConstInst((int) original, cpgen);
			}
			else if (conversion instanceof  F2D){
				inst = doubleConstInst( original, cpgen);
			}
			else{
				inst = longConstInst((long) original, cpgen);
			}


//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private void convertFromDouble(InstructionList instList, ConstantPoolGen cpgen){
		// looks for double constant and conversion, replaces with corresponding constant type
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(DCONST|LDC2_W) (D2F|D2I|D2L)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			double original = getDouble(match[0].getInstruction(), cpgen);
			Instruction conversion = match[1].getInstruction();
			Instruction inst;
			if (conversion instanceof  D2F) {
				inst = floatConstInst((float)original, cpgen);
			}
			else if (conversion instanceof  D2I) {
				inst = intConstInst((int) original, cpgen);
			}
			else {
				inst = longConstInst((long) original, cpgen);
			}


//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private void simpleIntFolding(InstructionList instList, ConstantPoolGen cpgen){
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

			// extract values from match[0] and match[1]
			int c1 = getInt(match[0].getInstruction(), cpgen);
			int c2 = getInt(match[1].getInstruction(), cpgen);

			// result doesn't need to be initialised tbh, intellij just super picky
			// result is value that we are folding
			int result = switch (match[2].getInstruction().getName()) {
				case "imul" -> c1 * c2;
				case "iadd" -> c1 + c2;
				case "isub" -> c1 - c2;
				case "idiv" -> c1 / c2;
				default -> 0;
			};

			// just a bunch of switch cases to know which operation to do

			// here toWrite is Instruction that we shall replace the (int) (int) (operator) instructions with
			Instruction inst = intConstInst(result, cpgen);

			replaceInst(match, inst, instList);
		}
	}

	private void simpleLongFolding(InstructionList instList, ConstantPoolGen cpgen) {
		//Initialise f and regex
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LDC2_W|LCONST) (LDC2_W|LCONST) (LADD|LSUB|LMUL|LDIV)";
		//iterate through each match
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			long l1, l2;
			l1 = getLong(match[0].getInstruction(), cpgen);
			l2 = getLong(match[1].getInstruction(), cpgen);

			// switch case for operations
			long result = switch (match[2].getInstruction().getName()) {
				case "lmul" -> l1 * l2;
				case "ladd" -> l1 + l2;
				case "lsub" -> l1 - l2;
				case "ldiv" -> l1 / l2;
				default -> 0;
			};

			// write result
			Instruction inst = longConstInst(result, cpgen);


//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private void simpleFloatFolding(InstructionList instList, ConstantPoolGen cpgen){
		//Initialise f and regex
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LDC_W|FCONST) (LDC_W|FCONST) (FADD|FSUB|FMUL|FDIV)";
		//iterate through each match
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			float f1, f2;
			f1 = getFloat(match[0].getInstruction(), cpgen);
			f2 = getFloat(match[1].getInstruction(), cpgen);

			// switch case for operations
			float result = switch (match[2].getInstruction().getName()) {
				case "fmul" -> f1 * f2;
				case "fadd" -> f1 + f2;
				case "fsub" -> f1 - f2;
				case "fdiv" -> f1 / f2;
				default -> 0;
			};

			// write result
			Instruction inst = floatConstInst(result, cpgen);


//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private void simpleDoubleFolding(InstructionList instList, ConstantPoolGen cpgen){
		//Initialise f and regex
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LDC2_W|DCONST) (LDC2_W|DCONST) (DADD|DSUB|DMUL|DDIV)";
		//iterate through each match
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			double d1, d2;
			d1 = getDouble(match[0].getInstruction(), cpgen);
			d2 = getDouble(match[1].getInstruction(), cpgen);

			// switch case for operations
			double result = switch (match[2].getInstruction().getName()) {
				case "dmul" -> d1 * d2;
				case "dadd" -> d1 + d2;
				case "dsub" -> d1 - d2;
				case "ddiv" -> d1 / d2;
				default -> 0;
			};

			// write result
			Instruction inst = doubleConstInst(result, cpgen);


//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private int getInt(Instruction inst, ConstantPoolGen cpgen){
		// just a helper function that takes an instruction we KNOW is an integer constant and extracts integer value
		int tmp;

		if(inst instanceof LDC){
			// cast inst to be LDC so we can get index of the constant within the constant pool
			// then we call cp.getConstant(index) to get the Constant object representing integer
			// cast Constant object to a ConstantInteger to that we can use getBytes to extract value from it
			tmp = ((ConstantInteger)((cpgen.getConstantPool()).getConstant(((LDC) inst).getIndex()))).getBytes();
		}
		else{
			// for ICONST, BIPUSH, SIPUSH, we can cast it to an ICONST object and do getValue
			// need to cast to integer since getValue returns a Number object
			tmp = (int)(((ConstantPushInstruction)inst).getValue());
		}
		return tmp;
	}

	private long getLong(Instruction inst, ConstantPoolGen cpgen){
		long tmp;
		if(inst instanceof LDC2_W){
			tmp = (long)((LDC2_W) inst).getValue(cpgen);
		}
		else{
			tmp = (long)(((LCONST)inst).getValue());
		}
		return tmp;
	}

	private float getFloat(Instruction inst, ConstantPoolGen cpgen){
		float tmp;
		if(inst instanceof LDC_W){
			tmp = (float)((LDC_W) inst).getValue(cpgen);
		}
		else{
			tmp = (float)(((FCONST)inst).getValue());
		}
		return tmp;

	}

	private double getDouble(Instruction inst, ConstantPoolGen cpgen){
		double tmp;
		if(inst instanceof LDC2_W){
			tmp = (double)((LDC2_W) inst).getValue(cpgen);
		}
		else{
			tmp = (double)(((DCONST)inst).getValue());
		}
		return tmp;
	}

	private Instruction intConstInst(int result, ConstantPoolGen cpgen){
		// takes an integer, generates an instruction to load that integer constant
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

		return toWrite;
	}

	private Instruction longConstInst(long result, ConstantPoolGen cpgen){
		// same as int, but for long
		Instruction toWrite;

		if (result == 0 || result == 1 ){
			toWrite = new LCONST(result);
		}
		else{
			int index = cpgen.addLong(result);
			toWrite = new LDC2_W(index);
		}

		return toWrite;
	}

	private Instruction floatConstInst(float result, ConstantPoolGen cpgen){
		// same as int, but for float
		Instruction toWrite;

		if (result == 0.0f || result == 1.0f || result == 2.0f){
			toWrite = new FCONST(result);
		}
		else{
			int index = cpgen.addFloat(result);
			toWrite = new LDC_W(index);
		}

		return toWrite;
	}

	private Instruction doubleConstInst(double result, ConstantPoolGen cpgen){
		// same as int, but for double
		Instruction toWrite;

		if (result == 0.0d || result == 1.0d ){
			toWrite = new DCONST(result);
		}
		else{
			int index = cpgen.addDouble(result);
			toWrite = new LDC2_W(index);
		}

		return toWrite;
	}

	private ArrayList<Integer> getConstantIntVars(InstructionList instList){
		// iterates through instList, if more than one istore/iinc for any index, we don't include that
		// final arraylist contains variables assigned value once only
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(ISTORE|IINC)";
		Hashtable<Integer, Boolean> vars = new Hashtable<>();
		ArrayList<Integer> constantVars = new ArrayList<>();

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			Instruction inst = match[0].getInstruction();
			int varIndex;
			if(inst instanceof ISTORE) {
				varIndex = ((ISTORE) inst).getIndex();
			}
			else{
				varIndex = ((IINC) inst).getIndex();
			}
			if (vars.containsKey(varIndex)){
				vars.put(varIndex, false);
			}
			else{
				vars.put(varIndex, true);
			}
		}
		Enumeration enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = (int)enu.nextElement();
			if(vars.get(key)){
				constantVars.add(key);
			}
		}
		return constantVars;
	}

	private ArrayList<Integer> getConstantLongVars(InstructionList instList){
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LSTORE)";
		Hashtable<Integer, Boolean> vars = new Hashtable<>();
		ArrayList<Integer> constantVars = new ArrayList<>();

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			Instruction inst = match[0].getInstruction();
			int varIndex;
			varIndex = ((LSTORE) inst).getIndex();
			if (vars.containsKey(varIndex)){
				vars.put(varIndex, false);
			}
			else{
				vars.put(varIndex, true);
			}
		}
		Enumeration enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = (int)enu.nextElement();
			if(vars.get(key)){
				constantVars.add(key);
			}
		}
		return constantVars;
	}

	private ArrayList<Integer> getConstantFloatVars(InstructionList instList){
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(FSTORE)";
		Hashtable<Integer, Boolean> vars = new Hashtable<>();
		ArrayList<Integer> constantVars = new ArrayList<>();

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			Instruction inst = match[0].getInstruction();
			int varIndex;
			varIndex = ((FSTORE) inst).getIndex();
			if (vars.containsKey(varIndex)){
				vars.put(varIndex, false);
			}
			else{
				vars.put(varIndex, true);
			}
		}
		Enumeration enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = (int)enu.nextElement();
			if(vars.get(key)){
				constantVars.add(key);
			}
		}
		return constantVars;
	}

	private ArrayList<Integer> getConstantDoubleVars(InstructionList instList){
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(DSTORE)";
		Hashtable<Integer, Boolean> vars = new Hashtable<>();
		ArrayList<Integer> constantVars = new ArrayList<>();

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			Instruction inst = match[0].getInstruction();
			int varIndex;
			varIndex = ((DSTORE) inst).getIndex();
			if (vars.containsKey(varIndex)){
				vars.put(varIndex, false);
			}
			else{
				vars.put(varIndex, true);
			}
		}
		Enumeration enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = (int)enu.nextElement();
			if(vars.get(key)){
				constantVars.add(key);
			}
		}
		return constantVars;
	}

	private void constantIntFolding(InstructionList instList, ConstantPoolGen cpgen, ArrayList<Integer> constantVars){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change

		// loads constant variables and their values into a hash table
		Hashtable<Integer, Integer> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(ICONST|BIPUSH|SIPUSH|LDC) (ISTORE)";

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((ISTORE)match[1].getInstruction()).getIndex();
			int varValue = getInt(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		// does a second pass through instList, and then replaces all iload with respective constant from variable
		pat = "(ILOAD)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((ILOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = intConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

	private void constantLongFolding(InstructionList instList, ConstantPoolGen cpgen, ArrayList<Integer> constantVars){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		Hashtable<Integer, Long> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LCONST|LDC2_W) (LSTORE)";

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((LSTORE)match[1].getInstruction()).getIndex();
			long varValue = getLong(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		pat = "(LLOAD)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((LLOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = longConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

	private void constantFloatFolding(InstructionList instList, ConstantPoolGen cpgen, ArrayList<Integer> constantVars){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		Hashtable<Integer, Float> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(FCONST|LDC_W) (FSTORE)";

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((FSTORE)match[1].getInstruction()).getIndex();
			float varValue = getFloat(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		pat = "(FLOAD)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((FLOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = floatConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

	private void constantDoubleFolding(InstructionList instList, ConstantPoolGen cpgen, ArrayList<Integer> constantVars){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		Hashtable<Integer, Double> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(DCONST|LDC2_w) (DSTORE)";

		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((DSTORE)match[1].getInstruction()).getIndex();
			double varValue = getDouble(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		pat = "(DLOAD)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((DLOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = doubleConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

//	private Hashtable<Integer, ArrayList<Integer>> getUntouchables(InstructionList instList, String store) {
	private ArrayList<ArrayList<Integer>> getUntouchables(InstructionList instList, String store) {
		// gets list of variables that should not be folded at position (key value)
		instList.setPositions(true);
		ArrayList<ArrayList<Integer>> notConstants = new ArrayList<>();
		boolean isConstant = false;
		InstructionHandle current = null;
		ArrayList<Integer> newNot;
		Hashtable<Integer, Integer> posToIndex= new Hashtable<>();
		for (int i = 0; i < instList.getLength(); i++) {
			if (i > 0){
				current = current.getNext();
				newNot = new ArrayList<>(notConstants.get(i-1));
			}
			else{
				current = instList.getStart();
				newNot = new ArrayList<>();
			}
			int currentPos = current.getPosition();
			posToIndex.put(currentPos, i);
			Instruction currentInst = current.getInstruction();
			if (currentInst instanceof ConstantPushInstruction || currentInst instanceof LDC || currentInst instanceof LDC2_W){
				isConstant = true;
			}
			else if (currentInst instanceof BranchInstruction){
				InstructionFinder f = new InstructionFinder(instList);
				String pat = "(IINC)";
				for (Iterator j = f.search(pat); j.hasNext(); ) {
					int fromIndex = current.getPosition();
					int jumpIndex = ((BranchInstruction)currentInst).getTarget().getPosition();
					InstructionHandle[] match = (InstructionHandle[]) j.next();
					int currentPosition = match[0].getPosition();
					// if iinc is in a loop
					if (currentPosition < fromIndex && currentPosition > jumpIndex) {
						// this is the indeterminable variable
						int changedVar = ((IINC) match[0].getInstruction()).getIndex();
						// within the entire loop, the variable is not determinable
						for (int k = jumpIndex; k < fromIndex - 1; k++) {
							if (!posToIndex.containsKey(k)){
								continue;
							}
							ArrayList<Integer> currentLine = notConstants.get(posToIndex.get(k));
							// remove changedVar from
							if(!currentLine.contains(Integer.valueOf(changedVar))){
								currentLine.add(Integer.valueOf(changedVar));
							}
						}
					}
				}
			}
			else if (currentInst instanceof StoreInstruction && isConstant){
				if (currentInst.getName().compareTo(store)==0){
					int nowConstant = ((StoreInstruction) currentInst).getIndex();
					if(newNot.contains(Integer.valueOf(nowConstant))){
						newNot.remove(Integer.valueOf(nowConstant));
					}
				}
			}
			notConstants.add(newNot);
		}
		return notConstants;

	}

	private void dynamicIntFolding(InstructionList instList, ConstantPoolGen cpgen){
		ArrayList<ArrayList<Integer>> untouchables = getUntouchables(instList, "ISTORE");
		InstructionHandle current = null;
		boolean lastInstConstant = false;
		Integer lastInt = null;
		Hashtable<Integer, Integer> varValues = new Hashtable<>();
		for (int i = 0; i < instList.getLength(); i++) {
			if(i == 0){
				current = instList.getStart();
			}
			else{
				current = current.getNext();
			}
			Instruction instCurrent = current.getInstruction();
			ArrayList<Integer> notConstant = untouchables.get(i);
//			System.out.println(instCurrent.toString(true));
			// loading valid int const
			if (instCurrent instanceof ConstantPushInstruction || instCurrent instanceof LDC || instCurrent instanceof LDC2_W){
				lastInstConstant = true;
				try {
//					System.out.println("found a constnat");
					lastInt = getInt(instCurrent, cpgen);
//					System.out.println(lastInt);
				}
				// if this exception pops up, we did not find an integer
				catch (ClassCastException c){
//					System.out.println("not constant");
					lastInstConstant = false;
				}
			}
			// storing AND last int was const
			else if (instCurrent instanceof StoreInstruction ){
				int varIndex = ((StoreInstruction) instCurrent).getIndex();
				if (lastInstConstant) {
					varValues.put(varIndex, lastInt);
					lastInstConstant = false;
//					System.out.println("storing " + lastInt + " in varIndex " + varIndex);
				}
				else{
					if (varValues.containsKey(varIndex)) {
						varValues.remove(varIndex);
//						System.out.println(varIndex + " no longer constant ");
					}
				}
			}
			// loading from valid var
			else if (instCurrent instanceof ILOAD){
				int varIndex = ((ILOAD) instCurrent).getIndex();
				if (varValues.containsKey(varIndex) && ! notConstant.contains(varIndex)){
					Instruction toWrite = intConstInst(varValues.get(varIndex), cpgen);
					replaceInst(current, toWrite, instList);
//					System.out.println("replacing " + varIndex + " with " + varValues.get(varIndex));
				}
			}
			else{
				lastInstConstant = false;
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
		System.out.print("method: ");
		System.out.println(method);
		Code methodCode = method.getCode();
		InstructionList instList = new InstructionList(methodCode.getCode());
		for(InstructionHandle handle: instList.getInstructionHandles())
		{
			Instruction inst = handle.getInstruction();
			System.out.println(handle.toString());
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