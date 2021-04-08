package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;


public class ConstantFolder
{
	boolean debug = false;
	boolean changed = true;
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
		//get constants
		ConstantPool cp = cpgen.getConstantPool();

		//bytecode array
		Code methodCode = method.getCode();
		InstructionList instList = new InstructionList(methodCode.getCode());

		// Initialise a method generator with the original method as the baseline
		MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(),
				null, method.getName(), cgen.getClassName(), instList, cpgen);

		simpleIntFolding(instList, cpgen);

		ArrayList<Integer> intConstVars = getConstantIntVars(instList, cpgen);
		ArrayList<Integer> longConstVars = getConstantLongVars(instList, cpgen);
		ArrayList<Integer> doubleConstVars = getConstantDoubleVars(instList, cpgen);
		ArrayList<Integer> floatConstVars = getConstantFloatVars(instList, cpgen);
		constantIntFolding(instList, cpgen, intConstVars);
		constantLongFolding(instList, cpgen, longConstVars);
		constantDoubleFolding(instList, cpgen, doubleConstVars);
		constantFloatFolding(instList, cpgen, floatConstVars);
		convertFromInt(instList, cpgen);
		convertFromLong(instList, cpgen);
		convertFromFloat(instList, cpgen);
		convertFromDouble(instList, cpgen);
		simpleIntFolding(instList, cpgen);
		simpleLongFolding(instList, cpgen);
		simpleFloatFolding(instList, cpgen);
		simpleDoubleFolding(instList, cpgen);


		// making sure goto statements in instList are ok
		instList.setPositions(true);

		// neccessary stuff to do to methodGen
		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		// since methodGen has reference to instList and cpgen, it will create new method with optimizations done
		Method newMethod = methodGen.getMethod();

		// replace method
		cgen.replaceMethod(method, newMethod);

		//keep repeating
		if(changed){
			changed = false;
//			System.out.println("something has changed, propagate stuff");
			optimizeMethod(cgen, cpgen, newMethod);
		}
	}

	private void replaceInst(InstructionHandle[] toReplace, Instruction replacement, InstructionList instList){
		for (InstructionHandle handle :
				toReplace) {
			if (handle == toReplace[0]){
				continue;
			}
			else if (handle.hasTargeters()){
				InstructionTargeter[] instTargs = handle.getTargeters();
				for (InstructionTargeter it :
						instTargs) {
					toReplace[0].addTargeter(it);
				}
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
			}
		}
	}

	private void replaceInst(InstructionHandle toReplace, Instruction replacement){
		toReplace.setInstruction(replacement);
	}

	private void convertFromInt(InstructionList instList, ConstantPoolGen cpgen){
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
				inst = longConstInst((long) original, cpgen);
			}
			else {
				inst = doubleConstInst((double) original, cpgen);
			}

//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
			changed = true;
		}
	}

	private void convertFromLong(InstructionList instList, ConstantPoolGen cpgen){
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
				inst = doubleConstInst((double) original, cpgen);
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
			Instruction inst = intConstInst(result, cpgen);

//			// insert toWrite before first Instruction in match
//			instList.insert(match[0],inst);
//
//			// delete all Instructions in match
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
			changed = true;
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
			long result = 0;
			switch (match[2].getInstruction().getName())
			{
				case "lmul":
					result = l1*l2;
					break;
				case "ladd":
					result = l1+l2;
					break;
				case "lsub":
					result = l1-l2;
					break;
				case "ldiv":
					result = l1/l2;
					break;
			}

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
			float result = 0;
			switch (match[2].getInstruction().getName())
			{
				case "fmul":
					result = f1*f2;
					break;
				case "fadd":
					result = f1+f2;
					break;
				case "fsub":
					result = f1-f2;
					break;
				case "fdiv":
					result = f1/f2;
					break;
			}

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
			double result = 0;
			switch (match[2].getInstruction().getName())
			{
				case "dmul":
					result = d1*d2;
					break;
				case "dadd":
					result = d1+d2;
					break;
				case "dsub":
					result = d1-d2;
					break;
				case "ddiv":
					result = d1/d2;
					break;
			}

			// write result
			Instruction inst = doubleConstInst(result, cpgen);


//			instList.insert(match[0], inst);
//
//			deleteInst(match, instList);
			replaceInst(match, inst, instList);
		}
	}

	private int getInt(Instruction inst, ConstantPoolGen cpgen){
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
		// get long from constantpool and instruction from pat - match[0] and match[1]
		long tmp;
		if(inst instanceof LDC2_W){
			tmp = (long)((LDC2_W) inst).getValue(cpgen);
		}
		else{
			// for ICONST, BIPUSH, SIPUSH, we can cast it to an ICONST object and do getValue
			// need to cast to integer since getValue returns a Number object
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
			// for ICONST, BIPUSH, SIPUSH, we can cast it to an ICONST object and do getValue
			// need to cast to integer since getValue returns a Number object
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
			// for ICONST, BIPUSH, SIPUSH, we can cast it to an ICONST object and do getValue
			// need to cast to integer since getValue returns a Number object
			tmp = (double)(((DCONST)inst).getValue());
		}
		return tmp;
	}

	private Instruction intConstInst(int result, ConstantPoolGen cpgen){
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

	private ArrayList<Integer> getConstantIntVars(InstructionList instList, ConstantPoolGen cpgen){
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

	private ArrayList<Integer> getConstantLongVars(InstructionList instList, ConstantPoolGen cpgen){
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

	private ArrayList<Integer> getConstantFloatVars(InstructionList instList, ConstantPoolGen cpgen){
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

	private ArrayList<Integer> getConstantDoubleVars(InstructionList instList, ConstantPoolGen cpgen){
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
		int lastLoadedInt = 0;
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

		pat = "(ILOAD)";
		for(Iterator i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();
			int varIndex = ((ILOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = intConstInst(vars.get(varIndex), cpgen);
//				instList.insert(match[0], toWrite);
//				try {
//					instList.delete(match[0]);
//				} catch (TargetLostException e) {
//					System.out.println("problem in iload" );
//					System.out.println(match[0].toString() + "not found in ");
//					System.out.println(instList.toString());
//					e.printStackTrace();
//				}
				replaceInst(match[0], toWrite);
				changed = true;
			}
		}

	}

	private void constantLongFolding(InstructionList instList, ConstantPoolGen cpgen, ArrayList<Integer> constantVars){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		float lastLoadedFloat = 0;
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
//			instList.insert(match[0], toWrite);
//			try {
//				instList.delete(match[0]);
//			} catch (TargetLostException e) {
//				e.printStackTrace();
//			}
				replaceInst(match[0], toWrite);
				changed = true;
			}
		}

	}

	private void constantFloatFolding(InstructionList instList, ConstantPoolGen cpgen, ArrayList<Integer> constantVars){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		float lastLoadedFloat = 0;
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
//			instList.insert(match[0], toWrite);
//			try {
//				instList.delete(match[0]);
//			} catch (TargetLostException e) {
//				e.printStackTrace();
//			}
				replaceInst(match[0], toWrite);
				changed = true;
			}
		}

	}

	private void constantDoubleFolding(InstructionList instList, ConstantPoolGen cpgen, ArrayList<Integer> constantVars){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		double lastLoadedDouble = 0;
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
//			instList.insert(match[0], toWrite);
//			try {
//				instList.delete(match[0]);
//			} catch (TargetLostException e) {
//				e.printStackTrace();
//			}
				replaceInst(match[0], toWrite);
				changed = true;
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
		ArrayList<Integer> intVars = new ArrayList<>();
		ArrayList<Integer> intDeterminable = new ArrayList<>();

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