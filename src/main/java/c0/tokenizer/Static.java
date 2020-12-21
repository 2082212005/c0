package c0.tokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.util.Pair;

import c0.error.CompileError;
import c0.error.TokenizeError;

public class Static {
	
	Tokenizer tokenizer;
	
	public Static(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }
	
	/** 当前偷看的 token */
    Token peekedToken = null;
	
	/**
     * 获取下一个 Token
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
     * 查看下一个 Token
     */
    private Token peek() throws TokenizeError {
        if (peekedToken == null) {
            peekedToken = tokenizer.nextToken();
        }
        return peekedToken;
    }
    
    /**
     * 如果下一个 token 的类型是 tt，则返回 true
     */
    private boolean check(TokenType tt) throws TokenizeError {
        var token = peek();
        return token.getTokenType() == tt;
    }
    
    List<String> kufunction = new ArrayList<String>();
    
    HashMap<Integer,String> global = new HashMap<>();
    
    List<Pair<String,Integer>> local = new ArrayList<>();
    
    @SuppressWarnings("unchecked")
	public Pair<HashMap<Integer,String>,List<Pair<String,Integer>>> analyse() throws CompileError {
    	int i=0;
    	init();
    	while(!check(TokenType.FN_KW)&&!check(TokenType.EOF))
    	{
    		if(check(TokenType.LET_KW)||check(TokenType.CONST_KW))
    		{
    			next();
    			global.put( i++, "0");
    		}
    		else
    			next();
    	}
    	while (!check(TokenType.EOF)) {
    		if(check(TokenType.FN_KW))
    		{
    			next();
    	    	int loc=0;
    			String name1 = next().getValue().toString();
    			while(!check(TokenType.L_BRACE))
    				next();
    			next();
    			int jishu=1;
    			while(jishu!=0)
    			{
    				if(check(TokenType.L_BRACE))
    				{
    					jishu++;
    					next();
    				}
    				else if(check(TokenType.R_BRACE))
    				{
    					jishu--;
    					next();
    				}
    				else if(check(TokenType.IDENT))
    	    		{
    	    			String name = next().getValue().toString();
    	    			if(kufunction.contains(name))
    	    				global.put(i++,name);
    	    		}
    				else if(check(TokenType.CONST_KW)||check(TokenType.LET_KW))
    				{
    					next();
    					loc++;
    				}
    				else
    					next();
    			}
    			global.put( i++, name1);
    			local.add(new Pair(name1,loc));
    		} 
    		else
    			next();
    	}
        @SuppressWarnings({ "unchecked", "rawtypes" })
		Pair<HashMap<Integer,String>,List<Pair<String,Integer>>> pair = new Pair(global,local);
        return pair;
    }

	private void init() {
		kufunction.add("getint");
		kufunction.add("getdouble");
		kufunction.add("getchar");
		kufunction.add("putint");
		kufunction.add("putdouble");
		kufunction.add("putchar");
		kufunction.add("putstr");
		kufunction.add("putln");
	}
    
}
