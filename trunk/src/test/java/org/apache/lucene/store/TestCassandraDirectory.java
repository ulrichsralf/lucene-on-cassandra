package org.apache.lucene.store;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

public class TestCassandraDirectory extends TestCase {
  
  CassandraDirectory cassandraDirectory;
  
  public void setUp() throws IOException {
    cassandraDirectory = new CassandraDirectory("lucene1", "index", 10, 10);
    for (String fileName : cassandraDirectory.listAll()) {
      cassandraDirectory.deleteFile(fileName);
    }
    cassandraDirectory.createOutput("sampleFile");
  }
  
  public void tearDown() throws IOException {
    if (cassandraDirectory != null) {
      cassandraDirectory.close();
    }
  }
  
  public void testListAll() throws IOException {
    String[] files = cassandraDirectory.listAll();
    assertEquals(1, files.length);
    assertEquals("sampleFile", files[0]);
  }
  
  public void testFileExists() throws IOException {
    assertTrue("The sample file should've been found, but it wasn't.",
        cassandraDirectory.fileExists("sampleFile"));
    try {
      cassandraDirectory.fileExists("dummyFile");
      assertTrue("The dummy file should not've been found, but it was.", true);
    } catch (IOException e) {}
  }
  
  public void testFileModified() throws IOException {
    long fileModified = cassandraDirectory.fileModified("sampleFile");
    long currentTime = new Date().getTime();
    long secondsSinceFileModified = (currentTime - fileModified) / 1000;
    assertTrue(
        "The sample file should have been modified just now, but it wasn't.",
        secondsSinceFileModified >= 0 && secondsSinceFileModified < 1);
  }
  
  public void testTouchFile() throws IOException, InterruptedException {
    long fileModified = cassandraDirectory.fileModified("sampleFile");
    TimeUnit.SECONDS.sleep(3);
    cassandraDirectory.touchFile("sampleFile");
    long fileTouched = cassandraDirectory.fileModified("sampleFile");
    long currentTime = new Date().getTime();
    long secondsFileUnmodified = (fileTouched - fileModified) / 1000;
    assertTrue(
        "The sample file was not quiet for 3 seconds before it was touched.",
        secondsFileUnmodified >= 2 && secondsFileUnmodified <= 3);
    long secondsSinceFileTouched = (currentTime - fileTouched) / 1000;
    assertTrue(
        "The sample file should'be been touched just now, but it wasn't.",
        secondsSinceFileTouched >= 0 && secondsSinceFileTouched < 1);
    
  }
  
  public void testDeleteFile() throws IOException {
    cassandraDirectory.deleteFile("sampleFile");
    assertTrue("The sample file should not've been found, but it was.",
        !cassandraDirectory.fileExists("sampleFile"));
  }
  
  public void testFileLength() throws IOException {
    assertEquals("The sample file's length should be zero.", cassandraDirectory
        .fileLength("sampleFile"), 0);
  }
  
  public void testCreateFile() throws IOException {
    for (int fileIndex = 0; fileIndex < 10; fileIndex++) {
      String testFileName = "testFile" + fileIndex;
      cassandraDirectory.createOutput(testFileName);
      cassandraDirectory.fileExists(testFileName);
      assertEquals("The test file's length should be zero.", cassandraDirectory
          .fileLength(testFileName), 0);
    }
  }
  
  public void testWriteFile() throws IOException {
    String smallerThanABlock = "0123";
    String exactlyABLock = "0123456789";
    String largerThanABLock = "0123456789A";
    
    String[] dataSample = new String[] {smallerThanABlock, exactlyABLock,
        largerThanABLock};
    
    for (String dataPoint : dataSample) {
      writeStrings(new String[] {dataPoint});
    }
    
    for (String dataPoint : dataSample) {
      List<String> stringsToBeWritten = new ArrayList<String>();
      stringsToBeWritten.addAll(Arrays.asList(dataSample));
      stringsToBeWritten.remove(dataPoint);
      writeStrings(stringsToBeWritten.toArray(new String[] {}));
    }
    
    PermutationGenerator kPermutation = new PermutationGenerator(
        dataSample.length);
    while (kPermutation.hasMore()) {
      int[] indices = kPermutation.getNext();
      String[] stringsToBeWritten = new String[3];
      for (int i = 0; i < indices.length; i++) {
        stringsToBeWritten[i] = dataSample[indices[i]];
      }
      writeStrings(dataSample);
    }
  }
  
  public void testReadFile() throws IOException {
    String smallerThanABlock = "0123";
    String exactlyABLock = "0123456789";
    String largerThanABLock = "0123456789A";
    
    String[] dataSample = new String[] {smallerThanABlock, exactlyABLock,
        largerThanABLock};
    
    for (String dataPoint : dataSample) {
      readStrings(writeStrings(new String[] {dataPoint}));
    }
    
    for (String dataPoint : dataSample) {
      List<String> stringsToBeWritten = new ArrayList<String>();
      stringsToBeWritten.addAll(Arrays.asList(dataSample));
      stringsToBeWritten.remove(dataPoint);
      readStrings(writeStrings(stringsToBeWritten.toArray(new String[] {})));
    }
    
    PermutationGenerator kPermutation = new PermutationGenerator(
        dataSample.length);
    while (kPermutation.hasMore()) {
      int[] indices = kPermutation.getNext();
      String[] stringsToBeWritten = new String[3];
      for (int i = 0; i < indices.length; i++) {
        stringsToBeWritten[i] = dataSample[indices[i]];
      }
      readStrings(writeStrings(dataSample));
    }
  }
  
  protected String[] writeStrings(String[] dataSample) throws IOException {
    cassandraDirectory.deleteFile("sampleFile");
    IndexOutput indexOutput = cassandraDirectory.createOutput("sampleFile");
    int dataLength = 0;
    for (String dataPoint : dataSample) {
      indexOutput.writeString(dataPoint);
      dataLength += dataPoint.length();
    }
    indexOutput.flush();
    assertEquals("The index output's current file length is incorrect.",
        dataLength + dataSample.length, indexOutput.length());
    return dataSample;
  }
  
  protected void readStrings(String[] dataSample) throws IOException {
    IndexInput indexInput = cassandraDirectory.openInput("sampleFile");
    int dataLength = 0;
    for (String expectedDataPoint : dataSample) {
      String actualDataPoint = indexInput.readString();
      assertEquals("The index input's next string did not match.",
          expectedDataPoint, actualDataPoint);
      dataLength += actualDataPoint.length();
    }
    assertEquals("The index output's current file length is incorrect.",
        dataLength + dataSample.length, indexInput.length());
  }
  
  public static class PermutationGenerator {
    
    private int[] a;
    private BigInteger numLeft;
    private BigInteger total;
    
    // -----------------------------------------------------------
    // Constructor. WARNING: Don't make n too large.
    // Recall that the number of permutations is n!
    // which can be very large, even when n is as small as 20 --
    // 20! = 2,432,902,008,176,640,000 and
    // 21! is too big to fit into a Java long, which is
    // why we use BigInteger instead.
    // ----------------------------------------------------------
    
    public PermutationGenerator(int n) {
      if (n < 1) {
        throw new IllegalArgumentException("Min 1");
      }
      a = new int[n];
      total = getFactorial(n);
      reset();
    }
    
    // ------
    // Reset
    // ------
    
    public void reset() {
      for (int i = 0; i < a.length; i++) {
        a[i] = i;
      }
      numLeft = new BigInteger(total.toString());
    }
    
    // ------------------------------------------------
    // Return number of permutations not yet generated
    // ------------------------------------------------
    
    public BigInteger getNumLeft() {
      return numLeft;
    }
    
    // ------------------------------------
    // Return total number of permutations
    // ------------------------------------
    
    public BigInteger getTotal() {
      return total;
    }
    
    // -----------------------------
    // Are there more permutations?
    // -----------------------------
    
    public boolean hasMore() {
      return numLeft.compareTo(BigInteger.ZERO) == 1;
    }
    
    // ------------------
    // Compute factorial
    // ------------------
    
    private static BigInteger getFactorial(int n) {
      BigInteger fact = BigInteger.ONE;
      for (int i = n; i > 1; i--) {
        fact = fact.multiply(new BigInteger(Integer.toString(i)));
      }
      return fact;
    }
    
    // --------------------------------------------------------
    // Generate next permutation (algorithm from Rosen p. 284)
    // --------------------------------------------------------
    
    public int[] getNext() {
      
      if (numLeft.equals(total)) {
        numLeft = numLeft.subtract(BigInteger.ONE);
        return a;
      }
      
      int temp;
      
      // Find largest index j with a[j] < a[j+1]
      
      int j = a.length - 2;
      while (a[j] > a[j + 1]) {
        j--;
      }
      
      // Find index k such that a[k] is smallest integer
      // greater than a[j] to the right of a[j]
      
      int k = a.length - 1;
      while (a[j] > a[k]) {
        k--;
      }
      
      // Interchange a[j] and a[k]
      
      temp = a[k];
      a[k] = a[j];
      a[j] = temp;
      
      // Put tail end of permutation after jth position in increasing order
      
      int r = a.length - 1;
      int s = j + 1;
      
      while (r > s) {
        temp = a[s];
        a[s] = a[r];
        a[r] = temp;
        r--;
        s++;
      }
      
      numLeft = numLeft.subtract(BigInteger.ONE);
      return a;
      
    }
    
  }
  
}
