package c0;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.math3.util.Pair;

import c0.analyser.Analyser;
import c0.error.CompileError;
import c0.error.TokenizeError;
import c0.instruction.Instruction;
import c0.tokenizer.Static;
import c0.tokenizer.StringIter;
import c0.tokenizer.Token;
import c0.tokenizer.TokenType;
import c0.tokenizer.Tokenizer;

import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class App {
	static List<String> kufunction = new ArrayList<String>();
	
	public static void main(String[] args) throws CompileError {
    	init();
		var argparse = buildArgparse();
        Namespace result;
        try {
            result = argparse.parseArgs(args);
        } catch (ArgumentParserException e1) {
            argparse.handleError(e1);
            System.exit(2);
            return;
        }

        var inputFileName = result.getString("input");
        var outputFileName = result.getString("output");

        InputStream input;
        if (inputFileName.equals("-")) {
            input = System.in;
        } else {
            try {
                input = new FileInputStream(inputFileName);
            } catch (FileNotFoundException e) {
                System.err.println("Cannot find input file.");
                e.printStackTrace();
                System.exit(2);
                return;
            }
        }

        PrintStream output;
        if (outputFileName.equals("-")) {
            output = System.out;
        } else {
            try {
                output = new PrintStream(new FileOutputStream(outputFileName));
            } catch (FileNotFoundException e) {
                System.err.println("Cannot open output file.");
                e.printStackTrace();
                System.exit(2);
                return;
            }
        }

        Scanner scanner;
        scanner = new Scanner(input);
        var iter = new StringIter(scanner);
        var tokenizer = tokenize(iter);
        
        
        InputStream input2;
        try {
			input2 = new FileInputStream(inputFileName);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
            System.exit(2);
            return;
		}
        
        Scanner scanner2;
        scanner2 = new Scanner(input2);
        var iter2 = new StringIter(scanner2);
        var tokenizer2 = tokenize(iter2);

        /*Static statics = new Static(tokenizer);
        HashMap<String,Integer> stas = statics.analyse();
        for(int i=0;i<stas.size();i++)
        {
        	output.print("static: ");
        	List<String> keyList = new ArrayList<>();
            for(String key: stas.keySet()){
                if(stas.get(key).equals(i)){
                    keyList.add(key);
                }
            }
            String name = keyList.get(0);
        	for(int j=0;j<name.length();i++)
        		output.print((int)name.charAt(i)+" ");
        	output.println("('"+name+"')");
        }*/
        
        if (result.getBoolean("tokenize")) {
            // tokenize
            try {
				var tokens = new ArrayList<Token>();
				while (true) {
				    var token = tokenizer.nextToken();
				    if (token.getTokenType().equals(TokenType.EOF)) {
				        break;
				    }
				    tokens.add(token);
				}
				for (Token token : tokens) {
				    output.println(token.toString());
				}
			} catch (TokenizeError e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.err.println(e);
                System.exit(2);
                return;
			}
        } else if (result.getBoolean("analyse")) {
            // analyze
        	try {
				Static statics = new Static(tokenizer);
				Pair<HashMap<Integer,String>,List<Pair<String,Integer>>> pair = statics.analyse();
				HashMap<Integer,String> stas = pair.getKey();
				List<Pair<String,Integer>> loc = pair.getValue();
				if(stas.size()>0) {
					output.println("72 30 3b 3e");
					output.println("00 00 00 01");
					output.println();
					output.println(hex32(stas.size()+1));
					output.println();
					output.println();
				}
				int func_num=0;
				for(int i=0;i<stas.size();i++)
				{
				    String name = stas.get(i);
				    if(name.equals("0"))
				    {
				    	output.println("00");
				    	output.println("00 00 00 08");
				    	output.println("00 00 00 00 00 00 00 00");
				    }
				    else
				    {
				    	output.println("01");
				    	output.println(hex32(name.length()));
				    	for(int j=0;j<name.length();j++)
				    		output.print("'"+name.charAt(j)+"' ");
				    	if(!kufunction.contains(name))
				    		func_num++;
				    	output.println();
				    }
				    output.println();
				}
				output.println("01");
		    	output.println("00 00 00 06");
		    	output.println("'_' 's' 't' 'a' 'r' 't'");
		    	output.println();
		    	output.println(hex32(func_num+1));
		    	output.println();
		    	output.println();
				/*for(int i=0;i<stas.size();i++)
				{
					output.print("static: ");
				    String name = stas.get(i);
				    if(name.equals("0"))
				    	output.println("0 0 0 0 0 0 0 0 (`\\u{0}\\u{0}\\u{0}\\u{0}\\u{0}\\u{0}\\u{0}\\u{0}`)");
				    else
				    {
				    	for(int j=0;j<name.length();j++)
				    		output.print(Integer.toHexString((int)(name.charAt(j)))+" ");
				    	output.println("(`"+name+"`)");
				    }
				    output.println();
				}
				output.println("static: 5F 73 74 61 72 74 (`_start`)");
				output.println();
				output.println();*/
				var analyzer = new Analyser(tokenizer2,stas,output,loc);
				List<Instruction> instructions;
				instructions = analyzer.analyse();
				for (Instruction instruction : instructions) {
				    output.println(instruction.toString());
				}
			} catch (CompileError e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.err.println(e);
                System.exit(2);
                return;
			}
        } 
        else {
            System.err.println("Please specify either '--analyse' or '--tokenize'.");
            System.exit(2);
        }
    }

    private static ArgumentParser buildArgparse() {
        var builder = ArgumentParsers.newFor("c0-java");
        var parser = builder.build();
        parser.addArgument("-t", "--tokenize").help("Tokenize the input").action(Arguments.storeTrue());
        parser.addArgument("-l", "--analyse").help("Analyze the input").action(Arguments.storeTrue());
        parser.addArgument("-o", "--output").help("Set the output file").required(true).dest("output")
                .action(Arguments.store());
        parser.addArgument("file").required(true).dest("input").action(Arguments.store()).help("Input file");
        return parser;
    }

    private static Tokenizer tokenize(StringIter iter) {
        var tokenizer = new Tokenizer(iter);
        return tokenizer;
    }
    
    /**
     * 8字节16进制数
     */
    private static String hex32(Integer x) {
    	String hex = Integer.toHexString(x);
		for(int z=hex.length();z<8;)
		{
			hex='0'+hex;
			z=hex.length();
		}
		if(x<0)
			hex=hex.substring(8);
		int q=0;
		for(int z=0;z<8;z++)
		{
			q+=1;
			if(z%2==1)
			{
				hex=hex.substring(0,q)+' '+hex.substring(q);
				q+=1;
			}
		}
		return hex;
    }
    
    /**
     * 16字节16进制数
     */
    private static String hex64(Integer x) {
    	String hex = Integer.toHexString(x);
		for(int z=hex.length();z<16;)
		{
			hex='0'+hex;
			z=hex.length();
		}
		int q=0;
		for(int z=0;z<16;z++)
		{
			q+=1;
			if(z%2==1)
			{
				hex=hex.substring(0,q)+' '+hex.substring(q);
				q+=1;
			}
		}
		return hex;
    }
    
    private static void init() {
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
