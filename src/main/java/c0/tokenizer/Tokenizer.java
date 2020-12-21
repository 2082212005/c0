package c0.tokenizer;

import c0.error.TokenizeError;
import c0.util.Pos;

import java.math.BigDecimal;

import c0.error.ErrorCode;

public class Tokenizer {

    private StringIter it;

    public Tokenizer(StringIter it) {
        this.it = it;
    }

    // 这里本来是想实现 Iterator<Token> 的，但是 Iterator 不允许抛异常，于是就这样了
    /**
     * 获取下一个 Token
     * 
     * @return
     * @throws TokenizeError 如果解析有异常则抛出
     */
    public Token nextToken() throws TokenizeError {
        it.readAll();

        // 跳过之前的所有空白字符
        skipSpaceCharacters();

        if (it.isEOF()) {
            return new Token(TokenType.EOF, "", it.currentPos(), it.currentPos());
        }

        char peek = it.peekChar();
        if (Character.isDigit(peek)) {
            return lexUInt();
        } else if (Character.isAlphabetic(peek)) {
            return lexIdentOrKeyword();
        }else if(peek=='"') {
        	return lexString();
        }else if(peek=='\'') {
        	return lexChar();
        }else {
            return lexOperatorOrUnknown();
        }
    }

    //uint、double
    private Token lexUInt() throws TokenizeError {
        String s="";
        Pos ppos=it.currentPos();
        while(Character.isDigit(it.peekChar()))
        	s=s+it.nextChar();
        if(it.peekChar()=='.')
        {
        	s+=it.nextChar();
        	if(!Character.isDigit(it.peekChar()))
        		throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
        	while(Character.isDigit(it.peekChar()))
             	s=s+it.nextChar();
        	if(it.peekChar()=='e'||it.peekChar()=='E')
        	{
        		s=s+it.nextChar();
        		if(it.peekChar()=='+'||it.peekChar()=='-')
        			s=s+it.nextChar();
        		if(!Character.isDigit(it.peekChar()))
            		throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
            	while(Character.isDigit(it.peekChar()))
                 	s=s+it.nextChar();
        	}
            while(s.length()>1&&s.charAt(0)=='0')
            	s=s.substring(1);
            if(s.charAt(0)=='.')
            	s='0'+s;
            BigDecimal bd = new BigDecimal(s);  
    	    double i =  Double.parseDouble(bd.toPlainString());
            return new Token(TokenType.DOUBLE_LITERAL, i, ppos, it.currentPos());
        }
        int k;
        for(k=0;s.length()>1&&s.charAt(k)=='0';)
        	s=s.substring(1);
        int i=Integer.parseInt(s);
        return new Token(TokenType.UINT_LITERAL, i, ppos, it.currentPos());
    }

    //ident
    private Token lexIdentOrKeyword() throws TokenizeError {
    	String s="";
        Pos ppos=it.currentPos();
        while(Character.isAlphabetic(it.peekChar())||Character.isDigit(it.peekChar())||it.peekChar()=='_')
        	s=s+it.nextChar();
        switch (s) {
        case "fn":
        	return new Token(TokenType.FN_KW, "fn", ppos, it.currentPos());
        case "let":
        	return new Token(TokenType.LET_KW, "let", ppos, it.currentPos());
        case "as":
        	return new Token(TokenType.AS_KW, "as", ppos, it.currentPos());
        case "const":
        	return new Token(TokenType.CONST_KW, "const", ppos, it.currentPos());
        case "while":
        	return new Token(TokenType.WHILE_KW, "while", ppos, it.currentPos());
        case "if":
        	return new Token(TokenType.IF_KW, "if", ppos, it.currentPos());
        case "else":
        	return new Token(TokenType.ELSE_KW, "else", ppos, it.currentPos());
        case "return":
        	return new Token(TokenType.RETURN_KW, "return", ppos, it.currentPos());
        case "break":
        	return new Token(TokenType.BREAK_KW, "break", ppos, it.currentPos());
        case "continue":
        	return new Token(TokenType.CONTINUE_KW, "continue", ppos, it.currentPos());
        case "int":
        	return new Token(TokenType.ty, "int", ppos, it.currentPos());
        case "void":
        	return new Token(TokenType.ty, "void", ppos, it.currentPos());
        case "double":
        	return new Token(TokenType.ty, "double", ppos, it.currentPos());
        default:
        	return new Token(TokenType.IDENT, s, ppos, it.currentPos());
        }
    }

    //字符
    private Token lexChar() throws TokenizeError{
    	char c;
        Pos ppos=it.currentPos();
        it.nextChar();
        if(it.peekChar()!='\\')
        	c=it.nextChar();
        else
        {
        	it.nextChar();
        	if(it.peekChar()!='n'&&it.peekChar()!='t'&&it.peekChar()!='\\'&&it.peekChar()!='"'&&it.peekChar()!='\''&&it.peekChar()!='r')
        		throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
        	else if(it.peekChar()=='n')
    		{
    			c='\n';
    			it.nextChar();
    		}
    		else if(it.peekChar()=='t')
        	{
    			c='\t';
    			it.nextChar();
        	}
    		else if(it.peekChar()=='r')
        	{
    			c='\r';
    			it.nextChar();
        	}
    		else
    			c=it.nextChar();
        }
        if(it.peekChar()!='\'')
        	throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
        it.nextChar();	
        return new Token(TokenType.CHAR_LITERAL, c, ppos, it.currentPos());
    }
    
    //字符串
    private Token lexString() throws TokenizeError{
    	String s="";
        Pos ppos=it.currentPos();
        it.nextChar();
        while(it.peekChar()!='"')
        {
        	if(it.peekChar()=='\\')
        	{
        		it.nextChar();
        		if(it.peekChar()!='n'&&it.peekChar()!='t'&&it.peekChar()!='\\'&&it.peekChar()!='"'&&it.peekChar()!='\''&&it.peekChar()!='r')
            		throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
        		else if(it.peekChar()=='n')
        		{
        			s+='\n';
        			it.nextChar();
        		}
        		else if(it.peekChar()=='t')
            	{
        			s+='\t';
        			it.nextChar();
            	}
        		else if(it.peekChar()=='r')
            	{
        			s+='\r';
        			it.nextChar();
            	}
        		else
        			s+=it.nextChar();
        		continue;
        	}	
        	s=s+it.nextChar();
        }	
        it.nextChar();	
        return new Token(TokenType.STRING_LITERAL, s, ppos, it.currentPos());
    }
    
    //注释
    private Token lexComment() throws TokenizeError{
    	String s="";
        Pos ppos=it.currentPos();
        while(it.peekChar()!='\n')
        	s=s+it.nextChar();	
        return new Token(TokenType.COMMENT, s, ppos, it.currentPos());
    }
    
    //其它
    private Token lexOperatorOrUnknown() throws TokenizeError {
        switch (it.nextChar()) {
            case '+':
                return new Token(TokenType.PLUS, '+', it.previousPos(), it.currentPos());

            case '-':
            	if(it.peekChar()=='>')
            	{
            		it.nextChar();
            		return new Token(TokenType.ARROW, "->", it.previousPos(), it.currentPos());
            	}	
            	return new Token(TokenType.MINUS, '-', it.previousPos(), it.currentPos());

            case '*':
            	return new Token(TokenType.MUL, '*', it.previousPos(), it.currentPos());

            case '/':
            	if(it.peekChar()=='/')
            	{
            		it.nextChar();
            		return lexComment();
            	}
            	return new Token(TokenType.DIV, '/', it.previousPos(), it.currentPos());
            	
            case '=':
            	if(it.peekChar()=='=')
            	{
            		it.nextChar();
            		return new Token(TokenType.EQ, "==", it.previousPos(), it.currentPos());
            	}
                return new Token(TokenType.ASSIGN, '=', it.previousPos(), it.currentPos());
                
            case ';':
                return new Token(TokenType.SEMICOLON, ';', it.previousPos(), it.currentPos());
                
            case ',':
                return new Token(TokenType.COMMA, ',', it.previousPos(), it.currentPos());
                
            case ':':
                return new Token(TokenType.COLON, ':', it.previousPos(), it.currentPos());
                
            case '(':
                return new Token(TokenType.L_PAREN, '(', it.previousPos(), it.currentPos());
                
            case ')':
                return new Token(TokenType.R_PAREN, ')', it.previousPos(), it.currentPos());
                
            case '{':
                return new Token(TokenType.L_BRACE, '{', it.previousPos(), it.currentPos());
                
            case '}':
                return new Token(TokenType.R_BRACE, '}', it.previousPos(), it.currentPos());
                
            case '>':
            	if(it.peekChar()=='=')
            	{
            		it.nextChar();
            		return new Token(TokenType.GE, ">=", it.previousPos(), it.currentPos());
            	}
                return new Token(TokenType.GT, '>', it.previousPos(), it.currentPos());
                
            case '<':
            	if(it.peekChar()=='=')
            	{
            		it.nextChar();
            		return new Token(TokenType.LE, "<=", it.previousPos(), it.currentPos());
            	}
                return new Token(TokenType.LT, '<', it.previousPos(), it.currentPos());
                
            case '!':
            	if(it.peekChar()=='=')
            	{
            		it.nextChar();
            		return new Token(TokenType.NEQ, "!=", it.previousPos(), it.currentPos());
            	}
            	throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());

            default:
                // 不认识这个输入，摸了
                throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
        }
    }

    private void skipSpaceCharacters() {
        while (!it.isEOF() && Character.isWhitespace(it.peekChar())) {
            it.nextChar();
        }
    }
}
