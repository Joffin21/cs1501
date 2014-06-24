/*************************************************************************
 *  Compilation:  javac LZW.java
 *  Execution:    java LZW - < input.txt   (compress)
 *  Execution:    java LZW + < input.txt   (expand)
 *  Dependencies: BinaryIn.java BinaryOut.java
 *
 *  Compress or expand binary input from standard input using LZW.
 *
 *
 *************************************************************************/
package assig3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LZWmod {
  final static Logger log = LoggerFactory.getLogger(LZWmod.class);

  private static final int R = 256;        // number of input chars
//  private static final int L = 4096;       // number of codewords = 2^W
  //private static final int W = 12;         // codeword width
  private static final double COMPRESSION_RATIO_THRESHOLD = 1.1;

  public static void compress(File inFile, File outFile, ResetCodewords reset) { 
    int W = 9;
    int L = (int) Math.pow(2, W);
    try {
      log.debug("Compressing file " + inFile.toPath());
      BinaryStdIn binaryIn = new BinaryStdIn(new FileInputStream(inFile));
      BinaryStdOut binaryOut = new BinaryStdOut(new PrintStream(new FileOutputStream(outFile)));
      int bitsUncompressed = 0;
      int bitsCompressed = 0;
      double originalCompressionRatio = 1;
      double currentCompressionRation = 1;
      double ratioOfRatios = 1;
      boolean monitoring = false;
      
      // write 2-bit flag for reset method used in compression
      switch (reset) {
      case NONE:
        log.info("Compression reset method: NONE");
        binaryOut.write(0, 2);
        break;
      case RESET:
        log.info("Compression reset method: RESET");
        binaryOut.write(1,2);
        break;
      case MONITOR:
        log.info("Compression reset method: MONITOR");
        binaryOut.write(2,2);
      }
      
      String input = binaryIn.readString();
      TST<Integer> st = new TST<Integer>();
      for (int i = 0; i < R; i++)
        st.put("" + (char) i, i);
      int code = R+1;  // R is codeword for EOF

      while (input.length() > 0) {
        String longestEncoding = st.longestPrefixOf(input);  // Find max prefix match s.
        binaryOut.write(st.get(longestEncoding), W);      // Print s's encoding.
        bitsCompressed += W;
        int t = longestEncoding.length();
        
        bitsUncompressed += t * 8; // TODO verify, java treats char as unicode 16 bit
        if (!monitoring)
          originalCompressionRatio = (double) bitsUncompressed / bitsCompressed;
        if (monitoring) {
          currentCompressionRation = (double) bitsUncompressed / bitsCompressed;
          ratioOfRatios = originalCompressionRatio/currentCompressionRation;
          //log.debug("Compress -- codewords: " + code +" compression ratio ratio: " + ratioOfRatios);
        }
        log.debug("COMPRESS code: "+code+" st: " + st + " t: " + t + " s: " + longestEncoding);

        if (code < L) {
          if (t < input.length())    // Add s to symbol table.
            st.put(input.substring(0, t + 1), code++);
        } else {
          switch (reset) {
          case RESET:
            log.debug("Compress -- resetting dict");
            st = new TST<Integer>();
            for (int i = 0; i < R; i++)
              st.put("" + (char) i, i);
            code = R+1;  // R is codeword for EOF
//            st.put(input.substring(0, t + 1), code++);
            break;
          case MONITOR:
            monitoring = true;
            if (ratioOfRatios >= COMPRESSION_RATIO_THRESHOLD) {
              log.debug("compression ratio threshold exceeded");
              log.debug("Compress -- resetting dict");
              st = new TST<Integer>();
              for (int i = 0; i < R; i++)
                st.put("" + (char) i, i);
              code = R+1;  // R is codeword for EOF
//              st.put(input.substring(0, t + 1), code++);
            }
            break;
          case NONE:
            break;
          }

        }
        input = input.substring(t);            // Scan past s in input.
      }
      binaryOut.write(R, W);
      binaryOut.close();
    } catch (FileNotFoundException ex) {
      log.error(ex.getMessage());
    }
  } 


  public static void expand(File inFile, File outFile) {
    int W = 9;
    int L = (int) Math.pow(2, W);
    log.debug("L: " + L);
    try {
      BinaryStdIn binaryIn = new BinaryStdIn(new FileInputStream(inFile));
      BinaryStdOut binaryOut = new BinaryStdOut(new PrintStream(new FileOutputStream(outFile)));
      
      int bitsUncompressed = 0;
      int bitsCompressed = 0;
      double originalCompressionRatio = 1;
      double currentCompressionRation = 1;
      double ratioOfRatios = 1;
      boolean monitoring = false;
      ResetCodewords reset = ResetCodewords.NONE;
      
      int resetFlag = binaryIn.readInt(2);
      log.debug("Expand reset flag: " + resetFlag);
      switch(resetFlag) {
      case 0:
        reset = ResetCodewords.NONE;
        log.info("Expand reset method: NONE");
        break;
      case 1:
        reset = ResetCodewords.RESET;
        log.info("Expand reset method: RESET");
        break;
      case 2:
        reset = ResetCodewords.MONITOR;
        log.info("Expand reset method: MONITOR");
        break;
      default:
        // invalid data read in (probably from old version of LZW compress
        // so reset input stream to reread valid compression bits using no reset method
        log.warn("Expand reset method: UNKNOWN -- resetting input stream");
        binaryIn.close();
        binaryIn = new BinaryStdIn(new FileInputStream(inFile));
        reset = ResetCodewords.NONE;
        break;
      }
      

      String[] st = new String[L];
      int freeCWIndex; // next available codeword value

      // initialize symbol table with all 1-character strings
      for (freeCWIndex = 0; freeCWIndex < R; freeCWIndex++)
        st[freeCWIndex] = "" + (char) freeCWIndex;
      
      st[freeCWIndex++] = "";                        // (unused) lookahead for EOF

      int compressedCode = binaryIn.readInt(W);
      bitsCompressed += W;

      String expandedVal = st[compressedCode];

      while (true) {
        binaryOut.write(expandedVal);
        bitsUncompressed += expandedVal.length() * 8; // TODO verify, java treats char as unicode 16 bit
        compressedCode = binaryIn.readInt(W);
        bitsCompressed += W;
        
        if (!monitoring)
          originalCompressionRatio = (double) bitsUncompressed / bitsCompressed;
        else {
          currentCompressionRation = (double) bitsUncompressed / bitsCompressed;
          ratioOfRatios = originalCompressionRatio/currentCompressionRation;
          //log.debug("Expand -- codewords: " + code +" compression ratio ratio: " + ratioOfRatios);
        }

        if (compressedCode == R) // EOF flag
          break;
        
        String newCodeword = st[compressedCode];
        if (freeCWIndex == compressedCode) 
          newCodeword = expandedVal + expandedVal.charAt(0);   // special case hack
        
        log.debug("EXPAND compressedCode: " + compressedCode + " newCodeword: " + newCodeword +" st: " + st +
            " expandedVal: " + expandedVal);

        if (freeCWIndex < L) {
          st[freeCWIndex++] = expandedVal + newCodeword.charAt(0);
        } else {
          switch (reset) {
          case RESET:
            log.debug("Expand -- resetting dict");
            st = new String[L];
            for (freeCWIndex = 0; freeCWIndex < R; freeCWIndex++)
              st[freeCWIndex] = "" + (char) freeCWIndex;
            // code = 256
            st[freeCWIndex++] = "";                        // (unused) lookahead for EOF
//            st[freeCWIndex++] = expandedVal + newCodeword.charAt(0);
            break;
          case MONITOR:
            monitoring = true;
            if (ratioOfRatios >= COMPRESSION_RATIO_THRESHOLD) {
              log.debug("compression ratio threshold exceeded");
              log.debug("Expand -- resetting dict");
              st = new String[L];
              for (freeCWIndex = 0; freeCWIndex < R; freeCWIndex++)
                st[freeCWIndex] = "" + (char) freeCWIndex;
              st[freeCWIndex++] = "";                        // (unused) lookahead for EOF
//              st[freeCWIndex++] = expandedVal + newCodeword.charAt(0);
            }
            break;
          case NONE:
            break;
          }
        }
        expandedVal = newCodeword;
      }
      binaryOut.close();
    } catch (FileNotFoundException ex) {
      System.err.println(ex.getMessage());
    }
  }



  public static void main(String[] args) {
     
    if (args[0].equals("-")) {
      if (args.length != 4) {
        System.out.println("usage: java assig3.LZWmod - <reset_method> <input_file> <output_file>");
        System.exit(1);
      }
      ResetCodewords resetMethod = ResetCodewords.NONE;
      switch(args[1].toLowerCase().charAt(0)) {
      case 'n':
        resetMethod = ResetCodewords.NONE;
        break;
      case 'r':
        resetMethod = ResetCodewords.RESET;
        break;
      case 'm':
        resetMethod = ResetCodewords.MONITOR;
        break;
      default:
        System.out.println("Invalid reset method");
        System.out.println("n = none, r = reset always, m = monitor then reset");
        System.exit(1);
        break;
      }
      compress(new File(args[2]), new File(args[3]),resetMethod);
    } else if (args[0].equals("+")) {
      if (args.length != 3) {
        System.out.println("usage: java assig3.LZWmod + <input_file> <output_file>");
        System.exit(1);
      }
      expand(new File(args[1]), new File(args[2]));
    } else {
      System.out.println("Enter - to compress or + to decomopress");
      System.exit(1);
    }
  }
  
  protected static enum ResetCodewords {
    NONE,
    MONITOR,
    RESET,
  }

}