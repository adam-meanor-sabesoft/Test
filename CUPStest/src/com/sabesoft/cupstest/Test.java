package com.sabesoft.cupstest;

import java.io.FileInputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import org.cups4j.CupsClient;
import org.cups4j.CupsPrinter;
import org.cups4j.PrintJob;
import org.cups4j.PrintJobAttributes;
import org.cups4j.PrintRequestResult;
import org.cups4j.WhichJobsEnum;
import org.cups4j.test.CupsTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Test {
	private static final Logger logger = LoggerFactory.getLogger(CupsTest.class);

	public static void main(final String... pArguments) throws Exception {
		// IP address of the printer server
		String host = "192.168.1.30";

		String printerName = null;
		boolean print = false;
		boolean getPrinters = false;
		boolean getJobs = false;
		boolean duplex = false;
		String fileName = null;
		String userName = null;
		String attributes = null;
		int copies = 1;
		String pages = null;

		// Parsing the command line to get the arguments
		try {
			if (pArguments.length == 0) {
				usage();
			}
			for (int i = 0; i < pArguments.length; i++) {
				if (pArguments[i].equals("-h")) {
					host = pArguments[++i];
				}
				else if (pArguments[i].equals("getPrinters")) {
					getPrinters = true;
				}
				else if (pArguments[i].equals("printFile")) {
					print = true;
					fileName = pArguments[++i];
					System.out.println("got the file");
				}
				else if (pArguments[i].equals("getJobs")) {
					getJobs = true;
				}
				else if (pArguments[i].equals("-u")) {
					userName = pArguments[++i];
					System.out.println(userName);
				}
				else if (pArguments[i].equals("-c")) {
					copies = Integer.parseInt(pArguments[++i]);
				}
				else if (pArguments[i].equals("-p")) {
					pages = pArguments[++i].trim();
				}
				else if (pArguments[i].equals("-P")) {
					printerName = pArguments[++i];
				}
				else if (pArguments[i].equals("-duplex")) {
					duplex = true;
				}
				else if (pArguments[i].equals("-job-attributes")) {
					attributes = pArguments[++i];
				}
				else if (pArguments[i].equals("-help")) {
					usage();
				}
			}

			if (getPrinters) {
				listPrintersOnHost(host);
			}
			if (print) {
				print(host, printerName, fileName, copies, pages, duplex, attributes);
				System.out.println(userName);
			}
			if (getJobs) {
				getJobs(host, userName, printerName);
			}
		}
		catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	private static void getJobs(String host, String userName, String printerName) throws Exception {
		if (host == null) {
			host = CupsClient.DEFAULT_HOST;
		}

		if (userName == null) {
			userName = CupsClient.DEFAULT_USER;
		}
		if (printerName == null) {
			CupsClient cupsClient = new CupsClient(host, CupsClient.DEFAULT_PORT, userName);
			printerName = cupsClient.getDefaultPrinter().getName();
		}

		CupsClient cupsClient = new CupsClient();

		// if user provided - get only jobs from this user.
		boolean myJobs = true;
		if (userName.equals(CupsClient.DEFAULT_USER)) {
			myJobs = false;
		}
		List<PrintJobAttributes> jobs = cupsClient.getJobs(
				new CupsPrinter(new URL("http://" + host + "/printers/" + printerName), printerName, false),
				WhichJobsEnum.ALL, userName, myJobs);

		for (PrintJobAttributes att : jobs) {
			PrintJobAttributes a = cupsClient.getJobAttributes(userName, att.getJobID());
			logger.info("job: " + a.getJobID() + " " + a.getJobName() + " " + a.getJobState() + " " + a.getPrinterURL()
					+ " " + a.getUserName());
		}
	}

	private static void print(String host, String printerName, String fileName, int copies, String pages,
			boolean duplex, String attributes) throws Exception {
		FileInputStream fileInputStream = new FileInputStream(fileName);

		CupsPrinter printer;// = null;
		CupsClient cupsClient = new CupsClient(host, CupsClient.DEFAULT_PORT);
		if (printerName == null) {
			printer = cupsClient.getDefaultPrinter();
		}
		else {
			printer = new CupsPrinter(
					new URL("http://" + host + ":" + CupsClient.DEFAULT_PORT + "/printers/" + printerName), printerName,
					false);
		}

		HashMap<String, String> attributeMap = new HashMap<String, String>();
		if (attributes != null) {
			attributeMap.put("job-attributes", attributes.replace("+", "#"));
		}

		PrintJob printJob = new PrintJob.Builder(fileInputStream).jobName("testJobName").userName("harald")
				.copies(copies).pageRanges(pages).duplex(duplex).attributes(attributeMap).build();

		PrintRequestResult printRequestResult = printer.print(printJob);
		if (printRequestResult.isSuccessfulResult()) {
			int jobID = printRequestResult.getJobId();

			logger.info("file sent to " + printer.getPrinterURL() + " jobID: " + jobID);
			logger.info("... current status = " + printer.getJobStatus(jobID));
			Thread.sleep(1000);
			logger.info("... status after 1 sec. = " + printer.getJobStatus(jobID));

			logger.info("Get last Printjob");
			PrintJobAttributes job = cupsClient.getJobAttributes(host, jobID);
			logger.info("ID: " + job.getJobID() + " user: " + job.getUserName() + " url: " + job.getJobURL()
					+ " status: " + job.getJobState());
		}
		else {
			// you might throw an exception or try to retry printing the job
			throw new Exception("print error! status code: " + printRequestResult.getResultCode()
					+ " status description: " + printRequestResult.getResultDescription());

		}

	}

	private static void listPrintersOnHost(String hostname) throws Exception {

		logger.info("List printers on " + hostname + ":");
		List<CupsPrinter> printers = null;
		long timeoutTime = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < timeoutTime && printers == null) {
			try {
				CupsClient cupsClient = new CupsClient(hostname, CupsClient.DEFAULT_PORT);
				printers = cupsClient.getPrinters();
			}
			catch (Exception e) {
				logger.error("could not get printers... retrying");
			}
		}

		if (printers == null || printers.isEmpty()) {
			throw new Exception("Error! Could not find any printers - check CUPS log files please.");
		}

		for (CupsPrinter p : printers) {
			logger.info(p.toString());
		}
	}

	private static void usage() {
		System.out.println(
				"CupsTest [-h <hostname>] [getPrinters][getJobs [-u <userName>][-P <printer name>]][printFile <file name> [-P <printer name>] [-c <copies>][-p <pages>][-duplex][-job-attributes <attributes>]] -help ");
		System.out.println("  <hostname>      - CUPS host name or ip adress (default: localhost)");
		System.out.println("  getPrinters     - list all printers from <hostname>");
		System.out.println("  getJobs         - list Jobs for given printer and user name on given host.");
		System.out.println(
				"                    defaults are: <hostname>=localhost, printer=default on <hostname>, user=anonymous");
		System.out.println("  printFile       - print the file provided in following parameter");
		System.out.println("  <filename>      - postscript file to print");
		System.out.println("  <printer name>  - printer name on <hostname>");
		System.out.println(
				"  <copies>        - number of copies (default: 1 wich means the document will be printed once)");
		System.out.println("  <pages>         - ranges of pages to print in the following syntax: ");
		System.out.println("                    1-2,4,6,10-12 - single ranges need to be in ascending order");
		System.out.println("  -duplex         - turns on double sided printing");
		System.out.println(
				"  <attributes>    - this is a list of additional print-job-attributes separated by '+' like:\n"
						+ "                    print-quality:enum:3+job-collation-type:enum:2");

		System.out.println("  -help           - shows this text");
	}
}
