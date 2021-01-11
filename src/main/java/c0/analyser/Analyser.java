package c0.analyser;

import c0.error.AnalyzeError;
import c0.error.CompileError;
import c0.error.ErrorCode;
import c0.error.ExpectedTokenError;
import c0.error.TokenizeError;
import c0.instruction.Instruction;
import c0.instruction.Operation;
import c0.tokenizer.Static;
import c0.tokenizer.Token;
import c0.tokenizer.TokenType;
import c0.tokenizer.Tokenizer;
import c0.util.Pos;

import java.io.PrintStream;
import java.util.*;

import org.apache.commons.math3.util.Pair;

public final class Analyser {

    Tokenizer tokenizer;
    ArrayList<Instruction> instructions;
    HashMap<Integer,String> stas;
    PrintStream output;

    /** 当前偷看的 token */
    Token peekedToken = null;

    /** 符号表 */
    HashMap<String, SymbolEntry> symbolTable = new HashMap<>();

    /** 下一个变量的栈偏移 */
    int nextOffset = 0;
    
    /**符号表层数*/
    int lay = 0;
    
    /**全局量*/   //static
    HashMap<String,Integer> global = new HashMap<>();
    
    /**局部变量数*/
    List<Pair<String,Integer>> local = new ArrayList<>();
    int local_num = 0;
    
    /**函数个数*/
    int function_num = 0;
    
    /**函数*/
    HashMap<String,Integer> function = new HashMap<>();
    
    /**操作集*/
    List<Pair<String,Long>> insructions = new ArrayList<Pair<String,Long>>();
    
    /**参数表*/
    HashMap<String,Integer> arga = new HashMap<>();
    int arga_num=0;
    
    /**局部变量表*/
    HashMap<String,Integer> loca = new HashMap<>();
    int loca_num=0;
    
    /**全局变量表*/
    HashMap<String,Integer> globa = new HashMap<>();
    int globa_num=0;
    
    /**标准库函数表*/
    List<String> ku = new ArrayList<>();
    
    /**CallName参数*/
    int CallNameNum = 0;
    
	public Analyser(Tokenizer tokenizer,HashMap<Integer,String> stas,PrintStream output,List<Pair<String,Integer>> local) {
        this.tokenizer = tokenizer;
        this.stas = stas;
        this.output = output;
        this.local = local;
        this.instructions = new ArrayList<>();
        int size=stas.size(),i=0;
        String [] key=new String[size]; 
        Integer [] val=new Integer[size];
        for(Integer a:stas.keySet()){//keySet 取出hashMap中的所有key
        	val[i]=a;
        	key[i]=stas.get(a);
        	i++;
        }
        for(int a=0;a<size;a++){
        	this.global.put(key[a],val[a]);
        }
    }

    public List<Instruction> analyse() throws CompileError {
    	analyseProgram();
        return instructions;
    }

    /**
     * 查看下一个 Token
     * 
     * @return
     * @throws TokenizeError
     */
    private Token peek() throws TokenizeError {
        if (peekedToken == null) {
            peekedToken = tokenizer.nextToken();
        }
        return peekedToken;
    }

    /**
     * 获取下一个 Token
     * 
     * @return
     * @throws TokenizeError
     */
    private Token next() throws TokenizeError {
        if (peekedToken != null) {
            var token = peekedToken;
            peekedToken = null;
            return token;
        } else {
            return tokenizer.nextToken();
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则返回 true
     * 
     * @param tt
     * @return
     * @throws TokenizeError
     */
    private boolean check(TokenType tt) throws TokenizeError {
        var token = peek();
        return token.getTokenType() == tt;
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回这个 token
     * 
     * @param tt 类型
     * @return 如果匹配则返回这个 token，否则返回 null
     * @throws TokenizeError
     */
    private Token nextIf(TokenType tt) throws TokenizeError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            return null;
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回，否则抛出异常
     * 
     * @param tt 类型
     * @return 这个 token
     * @throws CompileError 如果类型不匹配
     */
    private Token expect(TokenType tt) throws CompileError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            throw new ExpectedTokenError(tt, token);
        }
    }

    /**
     * 获取下一个变量的栈偏移
     * 
     * @return
     */
    private int getNextVariableOffset() {
        return this.nextOffset++;
    }

    /**
     * 添加一个符号
     * 
     * @param nameToken          名字
     * @param type               类型
     * @param isInitialized 是否已赋值
     * @param isConstant    是否是常量
     * @param layer1        符号表层数
     * @param curPos        当前 token 的位置（报错用）
     * @throws AnalyzeError 如果重复定义了则抛异常
     */
    private void addSymbol(String nameToken,String type, boolean isInitialized, boolean isConstant, int layer, Pos curPos) throws AnalyzeError {
        if ((this.symbolTable.get(nameToken) != null)&&(this.symbolTable.get(nameToken).layer==layer)) {
            throw new AnalyzeError(ErrorCode.DuplicateDeclaration, curPos);
        } else {
            this.symbolTable.put(nameToken, new SymbolEntry(type,isConstant, isInitialized, getNextVariableOffset(),layer));
        }
    }

    /**
     * 设置符号为已赋值
     * 
     * @param name   符号名称
     * @param curPos 当前位置（报错用）
     * @throws AnalyzeError 如果未定义则抛异常
     */
    private void declareSymbol(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            entry.setInitialized(true);
        }
    }

    /**
     * 获取变量在栈上的偏移
     * 
     * @param name   符号名
     * @param curPos 当前位置（报错用）
     * @return 栈偏移
     * @throws AnalyzeError
     */
    private int getOffset(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            return entry.getStackOffset();
        }
    }

    /**
     * 获取变量是否是常量
     * 
     * @param name   符号名
     * @param curPos 当前位置（报错用）
     * @return 是否为常量
     * @throws AnalyzeError
     */
    private boolean getIsConstant(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            return entry.isConstant();
        }
    }
    
    /**
     * 获取变量类型
     */
    private String getType(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            return entry.type;
        }
    }
    
    /**
     * 初始化标准库
     */
    private void init() {
    	this.symbolTable.put("getint", new SymbolEntry("f-int",true, true, getNextVariableOffset(),lay));
    	this.symbolTable.put("getdouble", new SymbolEntry("f-double",true, true, getNextVariableOffset(),lay));
    	this.symbolTable.put("getchar", new SymbolEntry("f-int",true, true, getNextVariableOffset(),lay));
    	this.symbolTable.put("putint", new SymbolEntry("f-void",true, true, getNextVariableOffset(),lay));
    	this.symbolTable.put("putdouble", new SymbolEntry("f-void",true, true, getNextVariableOffset(),lay));
    	this.symbolTable.put("putchar", new SymbolEntry("f-void",true, true, getNextVariableOffset(),lay));
    	this.symbolTable.put("putstr", new SymbolEntry("f-void",true, true, getNextVariableOffset(),lay));
    	this.symbolTable.put("putln", new SymbolEntry("f-void",true, true, getNextVariableOffset(),lay));
    	this.ku.add("getint");
    	this.ku.add("getdouble");
    	this.ku.add("getchar");
    	this.ku.add("putint");
    	this.ku.add("putdouble");
    	this.ku.add("putchar");
    	this.ku.add("putstr");
    	this.ku.add("putln");
    }
    
    /**
     * 程序结构
     */
    @SuppressWarnings("unchecked")
	private void analyseProgram() throws CompileError {
    	init();//导入标准库
    	while (check(TokenType.CONST_KW)||check(TokenType.LET_KW)) {
    		analyseDecl_stmt();
    	}
    	//找main
    	int mainnum = -1;
    	for(int i=0;i<this.local.size();i++)
    	{
    		if(this.local.get(i).getFirst().equals("main"))
    		{
    			mainnum=i+1;
    			break;
    		}
    	}
    	if(mainnum==-1)
    		throw new AnalyzeError(ErrorCode.NoMainFunction,new Pos(0,0));
    	this.insructions.add(new Pair<String, Long>("StackAlloc",(long) 0));
    	this.insructions.add(new Pair<String, Long>("Call",(long) mainnum));
    	output.println("fn ["+stas.size()+"] 0 0 -> 0 {");
    	//内容
    	int x=0;
    	for(Pair<String,Long> ins : this.insructions) {
    		output.print("    "+x+": "+ins.getKey());
    		if(ins.getValue()!=-1)
    			output.println("("+ins.getValue()+")");
    		else
    			output.println();
    		x++;
    	}
    	output.println("}");
    	output.println();
    	//清空表
    	this.insructions.clear();
    	while (check(TokenType.FN_KW)) {
    		analyseFunction();
    	}
    }
    
    /**
     * 函数
     */
    private void analyseFunction() throws CompileError {
    	int param_num = 0;
    	this.lay+=1;
    	expect(TokenType.FN_KW);
    	var nameToken = expect(TokenType.IDENT);
    	expect(TokenType.L_PAREN);
    	if(check(TokenType.CONST_KW)||check(TokenType.IDENT))
    	{
    		boolean IsConst=false;
    		if(check(TokenType.CONST_KW))
    		{
    			expect(TokenType.CONST_KW);
    			IsConst=true;
    		}
    		var nameToken1 = expect(TokenType.IDENT);
    		expect(TokenType.COLON);
    		var ty1 = expect(TokenType.ty);
    		addSymbol(nameToken1.getValue().toString(),ty1.getValue().toString(),true,IsConst,this.lay,nameToken1.getStartPos());
    		this.arga.put(nameToken1.getValue().toString(), this.arga_num++);
    		param_num++;
    		while(check(TokenType.COMMA))
        	{
    			expect(TokenType.COMMA);
    			if(check(TokenType.CONST_KW))
        		{
        			expect(TokenType.CONST_KW);
        			IsConst=true;
        		}
        		else
        			IsConst=false;
        		var nameToken2 = expect(TokenType.IDENT);
        		expect(TokenType.COLON);
        		var ty2 = expect(TokenType.ty);
        		addSymbol(nameToken2.getValue().toString(),ty2.getValue().toString(),true,IsConst,this.lay,nameToken2.getStartPos());
        		this.arga.put(nameToken2.getValue().toString(), this.arga_num++);
        		param_num++;
        	}
    	}
    	expect(TokenType.R_PAREN);
    	expect(TokenType.ARROW);
    	var ty = expect(TokenType.ty);
    	addSymbol(nameToken.getValue().toString(),"f-"+ty.getValue().toString(),true,false,this.lay-1,nameToken.getStartPos());
    	analyseBlock_stmt();
    	//移除局部变量
    	Iterator<SymbolEntry> it = this.symbolTable.values().iterator(); 
    	while(it.hasNext()) {  
    		SymbolEntry ele = it.next(); 
            if (ele.layer==this.lay) {
                it.remove();
            }
        }
    	this.lay-=1;
    	//函数个数
    	String name = nameToken.getValue().toString();
    	this.function_num++;
    	function.put(name, this.function_num);
    	//输出
    	this.insructions.add(new Pair<String, Long>("Ret",(long) -1));
    	int type = 1;
    	if(ty.getValue().toString().equals("void"))
    		type = 0;
    	output.println("fn ["+global.get(name)+"] "+local.get(this.local_num++).getSecond()+" "+param_num+" -> "+type+" {");
    	//内容
    	int x=0;
    	for(Pair<String,Long> ins : this.insructions) {
    		output.print("    "+x+": "+ins.getKey());
    		if(type == 1&&ins.getKey().equals("ArgA")) 
    			output.println("("+(ins.getValue()+1)+")");
    		else if(ins.getValue()!=-1)
    			output.println("("+ins.getValue()+")");
    		else
    			output.println();
    		x++;
    	}
    	output.println("}");
    	output.println();
    	
    	//清空表
    	this.arga.clear();
    	this.arga_num=0;
    	this.loca.clear();
    	this.loca_num=0;
    	this.insructions.clear();
    	this.CallNameNum++;
    }
    
    /**
     * 语句
     */
    private void analyseStmt() throws CompileError {
    	if(check(TokenType.IF_KW))
    		analyseIf_stmt();
    	else if(check(TokenType.WHILE_KW))
    		analyseWhile_stmt();
    	else if(check(TokenType.RETURN_KW))
    		analyseReturn_stmt();
    	else if(check(TokenType.SEMICOLON))
    		analyseEmpty_stmt();
    	else if(check(TokenType.L_BRACE))
    		analyseBlock_stmt();
    	else if(check(TokenType.CONST_KW))
    		analyseDecl_stmt();
    	else if(check(TokenType.LET_KW))
    		analyseDecl_stmt();
    	else
    		analyseExpr_stmt();
    }
    
    /**
     * 表达式（-1级）（=）
     */
    private String analyseAssign_expr() throws CompileError {
    	String str1 = null;
    	if(check(TokenType.IDENT)&&(getType(peek().getValue().toString(),peek().getStartPos()).charAt(0)!='f'))
		{
			Token ident = next();
			str1 = this.symbolTable.get(ident.getValue().toString()).type;
			if(check(TokenType.ASSIGN))
			{
				if(this.symbolTable.get(ident.getValue().toString()).isConstant)
					throw new AnalyzeError(ErrorCode.AssignToConstant,ident.getStartPos());
				if(this.arga.get(ident.getValue().toString())!=null)
					this.insructions.add(new Pair<String, Long>("ArgA",this.arga.get(ident.getValue().toString()).longValue()));
				else if(this.loca.get(ident.getValue().toString())!=null)
					this.insructions.add(new Pair<String, Long>("LocA",this.loca.get(ident.getValue().toString()).longValue()));
				else if(this.globa.get(ident.getValue().toString())!=null)
					this.insructions.add(new Pair<String, Long>("GlobA",this.globa.get(ident.getValue().toString()).longValue()));
				else
					throw new AnalyzeError(ErrorCode.NotDeclared,ident.getStartPos());
				var ass = expect(TokenType.ASSIGN);
				String str2 = analyseExpr();
				if(!str1.equals(str2))
		    		throw new AnalyzeError(ErrorCode.TypeMismatch,ass.getStartPos());
				str1 = "void";
				this.insructions.add(new Pair<String, Long>("Store64",(long) -1));
				declareSymbol(ident.getValue().toString(), ident.getStartPos());
			}
			else
				str1 = analyseExpr(ident);
		}
		else
			str1 = analyseExpr();
    	return str1;
    }
    
    /**
     * 表达式（-1级）（>、<、==、!=、<=、>=）
     */
    private String analyseCompare_expr() throws CompileError {
    	String str1 = analyseExpr();
    	if(check(TokenType.LE)||check(TokenType.LT)||check(TokenType.GE)||check(TokenType.GT)||check(TokenType.EQ)||check(TokenType.NEQ))
		{
			var compareSign = next();
			String str2 = analyseExpr();
			if(!str1.equals(str2))
				throw new AnalyzeError(ErrorCode.TypeMismatch,compareSign.getStartPos());
			else
			{
				if(compareSign.getValue().toString().equals(">"))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("CmpI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("CmpF",(long) -1));
					this.insructions.add(new Pair<String, Long>("SetGt",(long) -1));
				}
				else if(compareSign.getValue().toString().equals("<"))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("CmpI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("CmpF",(long) -1));
					this.insructions.add(new Pair<String, Long>("SetLt",(long) -1));
				}
				else if(compareSign.getValue().toString().equals(">="))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("CmpI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("CmpF",(long) -1));
					this.insructions.add(new Pair<String, Long>("SetLt",(long) -1));
					this.insructions.add(new Pair<String, Long>("Not",(long) -1));
				}
				else if(compareSign.getValue().toString().equals("<="))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("CmpI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("CmpF",(long) -1));
					this.insructions.add(new Pair<String, Long>("SetGt",(long) -1));
					this.insructions.add(new Pair<String, Long>("Not",(long) -1));
				}
				else if(compareSign.getValue().toString().equals("!="))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("CmpI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("CmpF",(long) -1));
				}
				else
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("CmpI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("CmpF",(long) -1));
					this.insructions.add(new Pair<String, Long>("Not",(long) -1));
				}
			}
			str1 = "boolean";
		}
    	return str1;
    }
    
    /**
     * 表达式（0级）（+、-）
     */
    private String analyseExpr() throws CompileError {
    	String str1 = analyseExpr1();
    	String str2 = str1;
    	var PlusOrMinus = new Token(TokenType.None,null,new Pos(0,0),new Pos(0,0));
    	while(check(TokenType.PLUS)||check(TokenType.MINUS))
		{
			PlusOrMinus = next();
			str2 = analyseExpr1();
			if(!str1.equals(str2))
				throw new AnalyzeError(ErrorCode.TypeMismatch,PlusOrMinus.getStartPos());
			else
			{
				if(PlusOrMinus.getValue().toString().equals("+"))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("AddI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("AddF",(long) -1));
				}
				else
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("SubI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("SubF",(long) -1));
				}
			}
		}
    	return str1;
    }
    
    private String analyseExpr(Token ident) throws CompileError{
    	String str1 = analyseExpr1(ident);
    	String str2 = str1;
    	var PlusOrMinus = new Token(TokenType.None,null,new Pos(0,0),new Pos(0,0));
    	while(check(TokenType.PLUS)||check(TokenType.MINUS))
		{
			PlusOrMinus = next();
			str2 = analyseExpr1();
			if(!str1.equals(str2))
				throw new AnalyzeError(ErrorCode.TypeMismatch,PlusOrMinus.getStartPos());
			else
			{
				if(PlusOrMinus.getValue().toString().equals("+"))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("AddI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("AddF",(long) -1));
				}
				else
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("SubI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("SubF",(long) -1));
				}
			}
		}
    	return str1;
    }
    
    /**
     * 表达式（1级）（*、/）
     */
    private String analyseExpr1() throws CompileError {
    	String str1 = analyseExpr2();
    	String str2 = str1;
    	var MulOrDiv = new Token(TokenType.None,null,new Pos(0,0),new Pos(0,0));
    	while(check(TokenType.MUL)||check(TokenType.DIV))
		{
			MulOrDiv = next();
			str2 = analyseExpr2();
			if(!str1.equals(str2))
				throw new AnalyzeError(ErrorCode.TypeMismatch,MulOrDiv.getStartPos());
			else
			{
				if(MulOrDiv.getValue().toString().equals("*"))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("MulI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("MulF",(long) -1));
				}
				else
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("DivI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("DivF",(long) -1));
				}
			}
		}
		return str1;
    }
    
    private String analyseExpr1(Token ident) throws CompileError {
    	String str1 = analyseExpr2(ident);
    	String str2 = str1;
    	var MulOrDiv = new Token(TokenType.None,null,new Pos(0,0),new Pos(0,0));
    	while(check(TokenType.MUL)||check(TokenType.DIV))
		{
			MulOrDiv = next();
			str2=analyseExpr2();
			if(!str1.equals(str2))
				throw new AnalyzeError(ErrorCode.TypeMismatch,MulOrDiv.getStartPos());
			else
			{
				if(MulOrDiv.getValue().toString().equals("*"))
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("MulI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("MulF",(long) -1));
				}
				else
				{
					if(str1.equals("int"))
						this.insructions.add(new Pair<String, Long>("DivI",(long) -1));
					else
						this.insructions.add(new Pair<String, Long>("DivF",(long) -1));
				}
			}
		}
		return str1;
    }
    
    /**
     * 表达式（2级）（as）
     */
    private String analyseExpr2() throws CompileError {
    	String str = analyseExpr3();
    	try {
			if(check(TokenType.AS_KW))
			{
				next();
				var ty = next();
				String ty2 = ty.getValue().toString();
				if(str.equals("int")&&ty2.equals("double"))
					this.insructions.add(new Pair<String, Long>("IToF",(long) -1));
				else if(ty2.equals("int")&&str.equals("double"))
					this.insructions.add(new Pair<String, Long>("FToI",(long) -1));
				return ty2;
			}
		} catch (TokenizeError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return str;
    }
    
    private String analyseExpr2(Token ident) throws CompileError {
    	String str = analyseExpr3(ident);
    	try {
			if(check(TokenType.AS_KW))
			{
				next();
				var ty = next();
				String ty2 = ty.getValue().toString();
				if(str.equals("int")&&ty2.equals("double"))
					this.insructions.add(new Pair<String, Long>("IToF",(long) -1));
				else if(ty2.equals("int")&&str.equals("double"))
					this.insructions.add(new Pair<String, Long>("FToI",(long) -1));
				return ty2;
			}
		} catch (TokenizeError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return str;
    }
    
    /**
     * 表达式（3级）（括号、函数、负号、Unit、Double、String、IDENT）
     */
    private String analyseExpr3() throws CompileError {
    	//括号
		if(check(TokenType.L_PAREN))
		{
			expect(TokenType.L_PAREN);
			String str = analyseCompare_expr();
			expect(TokenType.R_PAREN);
			return str;
		}
		//函数
		else if(check(TokenType.IDENT)&&(getType(peek().getValue().toString(),peek().getStartPos()).charAt(0)=='f'))
		{
			var fn = next();
			String str = this.symbolTable.get(fn.getValue().toString()).type.substring(2);
			if(str.equals("void"))
				this.insructions.add(new Pair<String, Long>("StackAlloc",(long) 0));
			else
				this.insructions.add(new Pair<String, Long>("StackAlloc",(long) 1));
			expect(TokenType.L_PAREN);
			while(!check(TokenType.R_PAREN))
			{
				analyseExpr();
				if(!check(TokenType.COMMA))
					break;
				expect(TokenType.COMMA);
			}
			expect(TokenType.R_PAREN);
			if(this.ku.contains(fn.getValue().toString()))
				this.insructions.add(new Pair<String, Long>("CallName",(long) this.CallNameNum++));
			else if(this.function.get(fn.getValue().toString())!=null)
				this.insructions.add(new Pair<String, Long>("Call",this.function.get(fn.getValue().toString()).longValue()));
			return str;
		}
		//负号
		else if(check(TokenType.MINUS))
		{
			expect(TokenType.MINUS);
			String str = analyseExpr();
			if(str.equals("int"))
				this.insructions.add(new Pair<String, Long>("NegI",(long) -1));
			else
				this.insructions.add(new Pair<String, Long>("NegF",(long) -1));
			return str;
		}
		else if(check(TokenType.UINT_LITERAL))
		{
			var nameToken = expect(TokenType.UINT_LITERAL);
			this.insructions.add(new Pair<String, Long>("Push",((Integer)(nameToken.getValue())).longValue()));
			return "int";
		}
		else if(check(TokenType.DOUBLE_LITERAL))
		{
			var nameToken = expect(TokenType.DOUBLE_LITERAL);
			this.insructions.add(new Pair<String, Long>("Push",Double.doubleToRawLongBits((Double) nameToken.getValue())));
			return "double";
		}
		else if(check(TokenType.STRING_LITERAL))
		{
			var nameToken = expect(TokenType.STRING_LITERAL);
			this.insructions.add(new Pair<String, Long>("Push",this.global.get(nameToken.getValue().toString()).longValue()));
			return "string";
		}
		else if(check(TokenType.IDENT))
		{
			var nameToken = expect(TokenType.IDENT);
			if(!this.symbolTable.get(nameToken.getValue().toString()).isInitialized)
				throw new AnalyzeError(ErrorCode.NotInitialized,nameToken.getStartPos());
			if(this.arga.get(nameToken.getValue().toString())!=null)
				this.insructions.add(new Pair<String, Long>("ArgA",this.arga.get(nameToken.getValue().toString()).longValue()));
			else if(this.loca.get(nameToken.getValue().toString())!=null)
				this.insructions.add(new Pair<String, Long>("LocA",this.loca.get(nameToken.getValue().toString()).longValue()));
			else if(this.globa.get(nameToken.getValue().toString())!=null)
				this.insructions.add(new Pair<String, Long>("GlobA",this.globa.get(nameToken.getValue().toString()).longValue()));
			else
				throw new AnalyzeError(ErrorCode.NotDeclared,nameToken.getStartPos());
			this.insructions.add(new Pair<String, Long>("Load64",(long) -1));
			return this.symbolTable.get(nameToken.getValue().toString()).type;
		}
		else
			throw new AnalyzeError(ErrorCode.InvalidPrint, peek().getStartPos());
    }
    
    private String analyseExpr3(Token nameToken) throws CompileError{
    	if(!this.symbolTable.get(nameToken.getValue().toString()).isInitialized)
			throw new AnalyzeError(ErrorCode.NotInitialized,nameToken.getStartPos());
		if(this.arga.get(nameToken.getValue().toString())!=null)
			this.insructions.add(new Pair<String, Long>("ArgA",this.arga.get(nameToken.getValue().toString()).longValue()));
		else if(this.loca.get(nameToken.getValue().toString())!=null)
			this.insructions.add(new Pair<String, Long>("LocA",this.loca.get(nameToken.getValue().toString()).longValue()));
		else if(this.globa.get(nameToken.getValue().toString())!=null)
			this.insructions.add(new Pair<String, Long>("GlobA",this.globa.get(nameToken.getValue().toString()).longValue()));
		else
			throw new AnalyzeError(ErrorCode.NotDeclared,nameToken.getStartPos());
		return this.symbolTable.get(nameToken.getValue().toString()).type;
    }
    
    /**
     * 表达式语句
     */
    private void analyseExpr_stmt() throws CompileError {
    	analyseAssign_expr();
    	expect(TokenType.SEMICOLON);
    }
    
    /**
     * if语句
     */
    private void analyseIf_stmt() throws CompileError {
    	expect(TokenType.IF_KW);
    	analyseCompare_expr();
    	this.insructions.add(new Pair<String, Long>("BrTure",(long) 1));
    	this.insructions.add(new Pair<String, Long>("Br",(long) -1));
    	int i=this.insructions.size();
    	int j=0;
    	analyseBlock_stmt();
    	if(check(TokenType.ELSE_KW))
    	{
    		expect(TokenType.ELSE_KW);
    		this.insructions.add(new Pair<String, Long>("Br",(long) 0));
    		j=this.insructions.size();
    		if(check(TokenType.IF_KW))
    			analyseIf_stmt();
    		else
    			analyseBlock_stmt();
    	}
    	this.insructions.add(new Pair<String, Long>("Br",(long) 0));
    	if(j!=0)
    	{
    		this.insructions.set((i-1),new Pair<String, Long>("Br",(long) (j-i)));
    		this.insructions.set((j-1),new Pair<String, Long>("Br",(long) (this.insructions.size()-j)));
    	}
    	else
    		this.insructions.set((i-1),new Pair<String, Long>("Br",(long) (this.insructions.size()-i)));
    }
    
    /**
     * while语句
     */
    private void analyseWhile_stmt() throws CompileError {
    	expect(TokenType.WHILE_KW);
    	this.insructions.add(new Pair<String, Long>("Br",(long) 0));
    	int i=this.insructions.size();
    	analyseCompare_expr();
    	this.insructions.add(new Pair<String, Long>("BrTure",(long) 1));
    	this.insructions.add(new Pair<String, Long>("Br",(long) -1));
    	int j=this.insructions.size();
    	analyseBlock_stmt();
    	this.insructions.add(new Pair<String, Long>("Br",(long) (i-this.insructions.size()-1)));
    	int k=this.insructions.size();
    	this.insructions.set((j-1),new Pair<String, Long>("Br",(long) (k-j)));
    }
    
    /**
     * return语句
     */
    private void analyseReturn_stmt() throws CompileError {
    	expect(TokenType.RETURN_KW);
    	if(check(TokenType.SEMICOLON))
    	{
    		expect(TokenType.SEMICOLON);
    	}
    	else
    	{
    		this.insructions.add(new Pair<String, Long>("ArgA",(long) -1));
    		analyseCompare_expr();
    		expect(TokenType.SEMICOLON);
    		this.insructions.add(new Pair<String, Long>("Store64",(long) -1));
    	}
    }
    
    /**
     * 空语句
     */
    private void analyseEmpty_stmt() throws CompileError {
    	expect(TokenType.SEMICOLON);
    }
    
    /**
     * 代码块
     */
    private void analyseBlock_stmt() throws CompileError {
    	expect(TokenType.L_BRACE);
    	while(!check(TokenType.R_BRACE))
    		analyseStmt();
    	expect(TokenType.R_BRACE);
    }
    
    /**
     * 声明语句
     */
    private void analyseDecl_stmt() throws CompileError {
    	if(nextIf(TokenType.CONST_KW) != null) {
    		if(this.lay==0) {
    			this.CallNameNum++;
    			this.insructions.add(new Pair<String, Long>("GlobA",(long) this.globa_num));
    		}else {
    			this.insructions.add(new Pair<String, Long>("LocA",(long) this.loca_num));
    		}
    		var nameToken = expect(TokenType.IDENT);
    		expect(TokenType.COLON);
    		var ty = expect(TokenType.ty);
    		expect(TokenType.ASSIGN);
    		analyseExpr();
    		expect(TokenType.SEMICOLON);
    		if(this.lay==0) {
    			this.globa.put(nameToken.getValue().toString(), this.globa_num++);
    		}else {
    			this.loca.put(nameToken.getValue().toString(), this.loca_num++);
    		}
    		addSymbol(nameToken.getValue().toString(),ty.getValue().toString(),true,true,this.lay,nameToken.getStartPos());
    		this.insructions.add(new Pair<String, Long>("Store64",(long) -1));
    	}
    	else if(nextIf(TokenType.LET_KW) != null) {
    		boolean isInitialized=false;
    		var nameToken = expect(TokenType.IDENT);
    		expect(TokenType.COLON);
    		var ty = expect(TokenType.ty);
    		if(check(TokenType.ASSIGN))
    		{
    			expect(TokenType.ASSIGN);
    			if(this.lay==0) {
    				this.insructions.add(new Pair<String, Long>("GlobA",(long) this.globa_num));
        		}else {
        			this.insructions.add(new Pair<String, Long>("LocA",(long) this.loca_num));
        		}
    			analyseExpr();
    			isInitialized=true;
    		}
    		if(this.lay==0) {
    			this.CallNameNum++;
    			this.globa.put(nameToken.getValue().toString(), this.globa_num++);
    		}else {
    			this.loca.put(nameToken.getValue().toString(), this.loca_num++);
    		}
    		expect(TokenType.SEMICOLON);
    		if(isInitialized==false)
    			addSymbol(nameToken.getValue().toString(),ty.getValue().toString(),false,false,this.lay,nameToken.getStartPos());
    		else
    		{
    			addSymbol(nameToken.getValue().toString(),ty.getValue().toString(),true,false,this.lay,nameToken.getStartPos());
    			this.insructions.add(new Pair<String, Long>("Store64",(long) -1));
    		}
    	}
    }
       
}
