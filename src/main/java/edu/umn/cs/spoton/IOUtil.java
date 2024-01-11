package edu.umn.cs.spoton;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class IOUtil {

  public static File createDirectory(File newDir) throws IOException {
    newDir.mkdirs();

    if (!newDir.isDirectory() || !newDir.canWrite()) {
      throw new IOException("Could not create directory: " + newDir.getAbsolutePath());
    } else {
      return newDir;
    }
  }

  public static void appendLineToFile(File file, String line) throws IOException {
    PrintWriter out = null;
    try {
      out = new PrintWriter(new FileWriter(file, true));
      out.println(line);
    } finally {
      out.close();
    }
  }

  public static void deleteFile(File dir) {
    if (dir.isDirectory()) {
      for (File sub : dir.listFiles()) {
        deleteFile(sub);
      }
    }
    dir.delete();
  }
}
