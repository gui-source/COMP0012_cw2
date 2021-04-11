package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;


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

		// done at the start so simple folding can use these changes
		constantIntFolding(instList, cpgen);
		constantLongFolding(instList, cpgen);
		constantDoubleFolding(instList, cpgen);
		constantFloatFolding(instList, cpgen);
		// same effect as constant folding
		dynamicIntFolding(instList, cpgen);
		dynamicLongFolding(instList, cpgen);
		dynamicFloatFolding(instList, cpgen);
		dynamicDoubleFolding(instList, cpgen);
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

		instList.setPositions(true);

		// necessary stuff to do to methodGen
		methodGen.setMaxStack();
		methodGen.setMaxLocals();

		// since methodGen has reference to instList and cpgen, it will create new method with optimizations done
		Method newMethod = methodGen.getMethod();

		// redo stackmap tables
		Attribute[] attributes = methodCode.getAttributes();
		for (Attribute a :
				attributes) {
			if(a instanceof StackMapTable){
				updateStackMapTable(newMethod, (StackMapTable) a);
			}
		}

		// replace method
		cgen.replaceMethod(method, newMethod);

		//keep repeating if any changes were made - the repeated optimisation bit happens here
		if(changed){
			changed = false;
			optimizeMethod(cgen, cpgen, newMethod);
		}
	}

	// takes the old StackMapTable for a method, and updates it according to the new method code
	private void updateStackMapTable(Method m, StackMapTable sm){
		// initialisation stuff
		StackMapTableEntry[] smte = sm.getStackMapTable();
		Attribute[] attributes = m.getAttributes();
		Code methodCode = m.getCode();
		InstructionList instList = new InstructionList(methodCode.getCode());

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(BranchInstruction)";

		Integer offset = null;

		// pos here refers to positions of where frames will be at
		ArrayList<Integer> pos = new ArrayList<>();

		// look for all jumps, and note down their target positions - this is where a frame will be
		for (Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext() ; ) {
			InstructionHandle[] match = i.next();
			int targetIndex = ((BranchInstruction) match[0].getInstruction()).getTarget().getPosition();
			pos.add(targetIndex);
		}

		// sort them in ascending order
		Collections.sort(pos);

		// for each position where a frame will be, update the corresponding bytecode offset in the stack frame
		for (int i = 0; i < pos.size(); i++)
		{
			// initialise targetIndex as position of jump, offset as offset delta for stack frame
			int targetIndex = pos.get(i);
			if(offset == null) {
				offset = targetIndex;
			}
			else{
				offset = targetIndex - 1 - offset;
			}

			StackMapTableEntry s = smte[i];
			String isSame = s.toString().substring(1, 6);

			// tag for SAME and SAME_LOCALS_1_STACK frame is just the offset/offset+64 respectively, so we need to
			// create new smte object instead of just changing the offset delta
			if (isSame.toLowerCase().compareTo("same_") == 0) {
				smte[i] = new StackMapTableEntry(offset+64,offset, s.getTypesOfLocals(), s.getTypesOfStackItems(), s.getConstantPool());

			}
			else if(isSame.toLowerCase().compareTo("same,") == 0){
				smte[i] = new StackMapTableEntry(offset ,offset, s.getTypesOfLocals(), s.getTypesOfStackItems(), s.getConstantPool());
			}
			// otherwise we just use setByteCodeOffsetDelta method - quick and easy
			else {
				smte[i].setByteCodeOffsetDelta(offset);
			}

			// update offset
			offset = targetIndex;
		}

		// set the attribute of Code object to be the StackMapTable we just updated
		for (Attribute a :
				attributes) {
			if(a instanceof Code){
				((Code) a).setAttributes(new Attribute[] {sm});
			}
		}
	}

	private void replaceInst(InstructionHandle[] toReplace, Instruction replacement, InstructionList instList){
		// solves a weird problem that causes exception if instruction to be deleted is referenced by a goto statement
		// this just takes the references and puts it onto the new replacement instruction
		for (InstructionHandle handle :
				toReplace){
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
			if (handle != toReplace[0])  {
				try {
					instList.delete(handle);
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
				instList.setPositions();
			}
		}

		instList.setPositions(true);
		changed = true;
	}

	private void replaceInst(InstructionHandle toReplace, Instruction replacement, InstructionList instList){
		// for 1 to 1 replacement of instruction
		toReplace.setInstruction(replacement);
		instList.setPositions(true);
		changed = true;
	}

	private void convertFromInt(InstructionList instList, ConstantPoolGen cpgen){
		// looks for integer constants, and i2f/i2d/i2l, replaces these two with a single float/double/long constant
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(ICONST|BIPUSH|SIPUSH|LDC) (I2F|I2D|I2L)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int original = (int) getConstant(match[0].getInstruction(), cpgen, int.class);

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

			replaceInst(match, inst, instList);
		}
	}

	private void convertFromLong(InstructionList instList, ConstantPoolGen cpgen){
		// looks for long constant and conversion, replaces with corresponding constant type
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LCONST|LDC2_W) (L2F|L2I|L2D)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			long original = (long) getConstant(match[0].getInstruction(), cpgen, long.class);
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

			replaceInst(match, inst, instList);
		}
	}

	private void convertFromFloat(InstructionList instList, ConstantPoolGen cpgen){
		// looks for float constant and conversion, replaces with corresponding constant type
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(FCONST|LDC_W) (F2I|F2D|F2L)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			float original = (float) getConstant(match[0].getInstruction(), cpgen, float.class);
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

			replaceInst(match, inst, instList);
		}
	}

	private void convertFromDouble(InstructionList instList, ConstantPoolGen cpgen){
		// looks for double constant and conversion, replaces with corresponding constant type
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(DCONST|LDC2_W) (D2F|D2I|D2L)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			double original = (double) getConstant(match[0].getInstruction(), cpgen, double.class);
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

			replaceInst(match, inst, instList);
		}
	}

	private void simpleIntFolding(InstructionList instList, ConstantPoolGen cpgen){
		// initialise InstructionFinder and regex
		InstructionFinder f = new InstructionFinder(instList);
		String pat ="(ICONST|BIPUSH|SIPUSH|LDC) (ICONST|BIPUSH|SIPUSH|LDC) (IMUL|IADD|IDIV|ISUB)";

		// standard for loop - iterate through i which contains results of search from f
		// while i.hasNext, we continue looping
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			// initialise match as an array of instructions
			// use instruction handle as easier way to deal with instructions this way
			// within match, first element is integer, second element integer, third element operator
			InstructionHandle[] match =  i.next();

			// extract values from match[0] and match[1]
			int c1 = (int) getConstant(match[0].getInstruction(), cpgen, int.class);
			int c2 = (int) getConstant(match[1].getInstruction(), cpgen, int.class);

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
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
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

			replaceInst(match, inst, instList);
		}
	}

	private void simpleFloatFolding(InstructionList instList, ConstantPoolGen cpgen){
		//Initialise f and regex
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LDC_W|FCONST) (LDC_W|FCONST) (FADD|FSUB|FMUL|FDIV)";
		//iterate through each match
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
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

			replaceInst(match, inst, instList);
		}
	}

	private void simpleDoubleFolding(InstructionList instList, ConstantPoolGen cpgen){
		//Initialise f and regex
		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LDC2_W|DCONST) (LDC2_W|DCONST) (DADD|DSUB|DMUL|DDIV)";
		//iterate through each match
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
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

	private <T> Number getConstant(Instruction inst, ConstantPoolGen cpgen, Class<T> constantType) throws ClassCastException{
		if (constantType == int.class){
			System.out.println("integer");
			return getInt(inst, cpgen);
		}
		else if (constantType == long.class){
			System.out.println("long");
			return getLong(inst, cpgen);
		}
		else if (constantType == float.class){
			System.out.println("float");
			return getFloat(inst, cpgen);
		}
		else if (constantType == double.class){
			System.out.println("double");
			return getDouble(inst, cpgen);
		}
		else{
			throw new ClassCastException("Instruction is not of suitable class");
		}
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

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = i.next();
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
		Enumeration<Integer> enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = enu.nextElement();
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

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
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
		Enumeration<Integer> enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = enu.nextElement();
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

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = i.next();
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
		Enumeration<Integer> enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = enu.nextElement();
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

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
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
		Enumeration<Integer> enu = vars.keys();
		while(enu.hasMoreElements()){
			int key = enu.nextElement();
			if(vars.get(key)){
				constantVars.add(key);
			}
		}
		return constantVars;
	}

	private void constantIntFolding(InstructionList instList, ConstantPoolGen cpgen){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		ArrayList<Integer> constantVars = getConstantIntVars(instList);

		// loads constant variables and their values into a hash table
		Hashtable<Integer, Integer> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(ICONST|BIPUSH|SIPUSH|LDC) (ISTORE)";

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int varIndex = ((ISTORE)match[1].getInstruction()).getIndex();
			int varValue = getInt(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		// does a second pass through instList, and then replaces all iload with respective constant from variable
		pat = "(ILOAD)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match = i.next();
			int varIndex = ((ILOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = intConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

	private void constantLongFolding(InstructionList instList, ConstantPoolGen cpgen){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		ArrayList<Integer> constantVars = getConstantLongVars(instList);

		Hashtable<Integer, Long> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(LCONST|LDC2_W) (LSTORE)";

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int varIndex = ((LSTORE)match[1].getInstruction()).getIndex();
			long varValue = getLong(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		pat = "(LLOAD)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int varIndex = ((LLOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = longConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

	private void constantFloatFolding(InstructionList instList, ConstantPoolGen cpgen){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		ArrayList<Integer> constantVars = getConstantFloatVars(instList);
		Hashtable<Integer, Float> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(FCONST|LDC_W) (FSTORE)";

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int varIndex = ((FSTORE)match[1].getInstruction()).getIndex();
			float varValue = getFloat(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		pat = "(FLOAD)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int varIndex = ((FLOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = floatConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

	private void constantDoubleFolding(InstructionList instList, ConstantPoolGen cpgen){
		// pass an arraylist containing variable indexes
		// variable indexes refer to variables that are assigned constant values (one number) and never change
		ArrayList<Integer> constantVars = getConstantDoubleVars(instList);
		Hashtable<Integer, Double> vars = new Hashtable<>();

		InstructionFinder f = new InstructionFinder(instList);
		String pat = "(DCONST|LDC2_w) (DSTORE)";

		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int varIndex = ((DSTORE)match[1].getInstruction()).getIndex();
			double varValue = getDouble(match[0].getInstruction(), cpgen);
			if(constantVars.contains(varIndex)){
				vars.put(varIndex, varValue);
			}
		}

		pat = "(DLOAD)";
		for(Iterator<InstructionHandle[]> i = f.search(pat); i.hasNext();) {
			InstructionHandle[] match =  i.next();
			int varIndex = ((DLOAD)match[0].getInstruction()).getIndex();
			if (vars.containsKey(varIndex)){
				Instruction toWrite = doubleConstInst(vars.get(varIndex), cpgen);
				replaceInst(match[0], toWrite, instList);
			}
		}

	}

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
				for (Iterator<InstructionHandle[]> j = f.search(pat); j.hasNext(); ) {
					int fromIndex = current.getPosition();
					int jumpIndex = ((BranchInstruction)currentInst).getTarget().getPosition();
					InstructionHandle[] match =  j.next();
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
							if(!currentLine.contains(changedVar)){
								currentLine.add(changedVar);
							}
						}
					}
				}
			}
			else if (currentInst instanceof StoreInstruction && isConstant){
				if (currentInst.getName().compareTo(store)==0){
					int nowConstant = ((StoreInstruction) currentInst).getIndex();
					newNot.remove(Integer.valueOf(nowConstant));
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
			// loading valid int const
			if (instCurrent instanceof ConstantPushInstruction || instCurrent instanceof LDC || instCurrent instanceof LDC2_W){
				lastInstConstant = true;
				try {
					lastInt = getInt(instCurrent, cpgen);
				}
				// if this exception pops up, we did not find an integer
				catch (ClassCastException c){
					lastInstConstant = false;
				}
			}
			// storing AND last int was const
			else if (instCurrent instanceof StoreInstruction ){
				int varIndex = ((StoreInstruction) instCurrent).getIndex();
				if (lastInstConstant) {
					varValues.put(varIndex, lastInt);
					lastInstConstant = false;
				}
				else{
					varValues.remove(varIndex);
				}
			}
			// loading from valid var
			else if (instCurrent instanceof ILOAD){
				int varIndex = ((ILOAD) instCurrent).getIndex();
				if (varValues.containsKey(varIndex) && ! notConstant.contains(varIndex)){
					Instruction toWrite = intConstInst(varValues.get(varIndex), cpgen);
					replaceInst(current, toWrite, instList);
				}
			}
			else{
				lastInstConstant = false;
			}
		}
	}

	private void dynamicLongFolding(InstructionList instList, ConstantPoolGen cpgen){
		ArrayList<ArrayList<Integer>> untouchables = getUntouchables(instList, "LSTORE");
		InstructionHandle current = null;
		boolean lastInstConstant = false;
		Long lastLong = null;
		Hashtable<Integer, Long> varValues = new Hashtable<>();
		for (int i = 0; i < instList.getLength(); i++) {
			if(i == 0){
				current = instList.getStart();
			}
			else{
				current = current.getNext();
			}
			Instruction instCurrent = current.getInstruction();
			ArrayList<Integer> notConstant = untouchables.get(i);
			// loading valid int const
			if (instCurrent instanceof ConstantPushInstruction || instCurrent instanceof LDC || instCurrent instanceof LDC2_W){
				lastInstConstant = true;
				try {
					lastLong = getLong(instCurrent, cpgen);
				}
				// if this exception pops up, we did not find an integer
				catch (ClassCastException c){
					lastInstConstant = false;
				}
			}
			// storing AND last int was const
			else if (instCurrent instanceof StoreInstruction ){
				int varIndex = ((StoreInstruction) instCurrent).getIndex();
				if (lastInstConstant) {
					varValues.put(varIndex, lastLong);
					lastInstConstant = false;
				}
				else{
					varValues.remove(varIndex);
				}
			}
			// loading from valid var
			else if (instCurrent instanceof LLOAD){
				int varIndex = ((LLOAD) instCurrent).getIndex();
				if (varValues.containsKey(varIndex) && ! notConstant.contains(varIndex)){
					Instruction toWrite = longConstInst(varValues.get(varIndex), cpgen);
					replaceInst(current, toWrite, instList);
				}
			}
			else{
				lastInstConstant = false;
			}
		}
	}

	private void dynamicFloatFolding(InstructionList instList, ConstantPoolGen cpgen){
		ArrayList<ArrayList<Integer>> untouchables = getUntouchables(instList, "FSTORE");
		InstructionHandle current = null;
		boolean lastInstConstant = false;
		Float lastFloat = null;
		Hashtable<Integer, Float> varValues = new Hashtable<>();
		for (int i = 0; i < instList.getLength(); i++) {
			if(i == 0){
				current = instList.getStart();
			}
			else{
				current = current.getNext();
			}
			Instruction instCurrent = current.getInstruction();
			ArrayList<Integer> notConstant = untouchables.get(i);
			// loading valid int const
			if (instCurrent instanceof ConstantPushInstruction || instCurrent instanceof LDC || instCurrent instanceof LDC2_W){
				lastInstConstant = true;
				try {
					lastFloat = getFloat(instCurrent, cpgen);
				}
				// if this exception pops up, we did not find an integer
				catch (ClassCastException c){
					lastInstConstant = false;
				}
			}
			// storing AND last int was const
			else if (instCurrent instanceof StoreInstruction ){
				int varIndex = ((StoreInstruction) instCurrent).getIndex();
				if (lastInstConstant) {
					varValues.put(varIndex, lastFloat);
					lastInstConstant = false;
				}
				else{
					varValues.remove(varIndex);
				}
			}
			// loading from valid var
			else if (instCurrent instanceof FLOAD){
				int varIndex = ((FLOAD) instCurrent).getIndex();
				if (varValues.containsKey(varIndex) && ! notConstant.contains(varIndex)){
					Instruction toWrite = floatConstInst(varValues.get(varIndex), cpgen);
					replaceInst(current, toWrite, instList);
				}
			}
			else{
				lastInstConstant = false;
			}
		}
	}

	private void dynamicDoubleFolding(InstructionList instList, ConstantPoolGen cpgen){
		ArrayList<ArrayList<Integer>> untouchables = getUntouchables(instList, "DSTORE");
		InstructionHandle current = null;
		boolean lastInstConstant = false;
		Double lastDouble = null;
		Hashtable<Integer, Double> varValues = new Hashtable<>();
		for (int i = 0; i < instList.getLength(); i++) {
			if(i == 0){
				current = instList.getStart();
			}
			else{
				current = current.getNext();
			}
			Instruction instCurrent = current.getInstruction();
			ArrayList<Integer> notConstant = untouchables.get(i);
			// loading valid int const
			if (instCurrent instanceof ConstantPushInstruction || instCurrent instanceof LDC || instCurrent instanceof LDC2_W){
				lastInstConstant = true;
				try {
					lastDouble = getDouble(instCurrent, cpgen);
				}
				// if this exception pops up, we did not find an integer
				catch (ClassCastException c){
					lastInstConstant = false;
				}
			}
			// storing AND last int was const
			else if (instCurrent instanceof StoreInstruction ){
				int varIndex = ((StoreInstruction) instCurrent).getIndex();
				if (lastInstConstant) {
					varValues.put(varIndex, lastDouble);
					lastInstConstant = false;
				}
				else{
					varValues.remove(varIndex);
				}
			}
			// loading from valid var
			else if (instCurrent instanceof DLOAD){
				int varIndex = ((DLOAD) instCurrent).getIndex();
				if (varValues.containsKey(varIndex) && ! notConstant.contains(varIndex)){
					Instruction toWrite = doubleConstInst(varValues.get(varIndex), cpgen);
					replaceInst(current, toWrite, instList);
				}
			}
			else{
				lastInstConstant = false;
			}
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