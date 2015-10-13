package martin.common;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class  InputStreamDumper implements Runnable {
	private InputStream stream;
	private BufferedOutputStream outStream;

	public InputStreamDumper(InputStream stream){
		this.stream = stream;
	}
	
	public InputStreamDumper(InputStream stream, OutputStream outStream){
		this.stream = stream;
		this.outStream = new BufferedOutputStream(outStream);
	}
	
	public InputStreamDumper(InputStream stream, File outFile){
		this.stream = stream;
		
		try {
			this.outStream = outFile != null ? new BufferedOutputStream(new FileOutputStream(outFile)) : null;
		} catch (FileNotFoundException e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void run() {
		byte[] b = new byte[1024];

		try{
			int read = -1;
			do {
				read = stream.read(b);
				
				if (read > 0 && outStream != null)
					outStream.write(b, 0, read);
				
			} while (read != -1);

			if (outStream != null)
				outStream.close();
			
			stream.close();
			
			outStream = null;
			stream = null;
			
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	public void closeAll(){/*
		try{
			if (stream != null)
				stream.close();
			if (outStream != null)
				outStream.close();
		} catch (Exception e){
			
		}*/
	}
}
