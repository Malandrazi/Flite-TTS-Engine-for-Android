/*************************************************************************/
/*                                                                       */
/*                  Language Technologies Institute                      */
/*                     Carnegie Mellon University                        */
/*                         Copyright (c) 2010                            */
/*                        All Rights Reserved.                           */
/*                                                                       */
/*  Permission is hereby granted, free of charge, to use and distribute  */
/*  this software and its documentation without restriction, including   */
/*  without limitation the rights to use, copy, modify, merge, publish,  */
/*  distribute, sublicense, and/or sell copies of this work, and to      */
/*  permit persons to whom this work is furnished to do so, subject to   */
/*  the following conditions:                                            */
/*   1. The code must retain the above copyright notice, this list of    */
/*      conditions and the following disclaimer.                         */
/*   2. Any modifications must be clearly marked as such.                */
/*   3. Original authors' names are not deleted.                         */
/*   4. The authors' names are not used to endorse or promote products   */
/*      derived from this software without specific prior written        */
/*      permission.                                                      */
/*                                                                       */
/*  CARNEGIE MELLON UNIVERSITY AND THE CONTRIBUTORS TO THIS WORK         */
/*  DISCLAIM ALL WARRANTIES WITH REGARD TO THIS SOFTWARE, INCLUDING      */
/*  ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO EVENT   */
/*  SHALL CARNEGIE MELLON UNIVERSITY NOR THE CONTRIBUTORS BE LIABLE      */
/*  FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES    */
/*  WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN   */
/*  AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,          */
/*  ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF       */
/*  THIS SOFTWARE.                                                       */
/*                                                                       */
/*************************************************************************/
/*             Author:  Alok Parlikar (aup@cs.cmu.edu)                   */
/*               Date:  April 2010                                       */
/*************************************************************************/

package edu.cmu.cs.speech.tts.flite;

import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.os.Environment;
import java.io.File;
import android.util.Log;
import edu.cmu.cs.speech.tts.flite.Utility;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.math.BigInteger;

/* Checks if the voice data is installed
 * for flite
 */

public class CheckVoiceData extends Activity {
	private final static String LOG_TAG = "Flite_Java_" + CheckVoiceData.class.getSimpleName();
	private final static String FLITE_DATA_PATH = Environment.getExternalStorageDirectory()
			+ "/flite-data/";

	public static String getDataPath() {
		return FLITE_DATA_PATH;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		int result = TextToSpeech.Engine.CHECK_VOICE_DATA_PASS;
		Intent returnData = new Intent();
		returnData.putExtra(TextToSpeech.Engine.EXTRA_VOICE_DATA_ROOT_DIRECTORY, FLITE_DATA_PATH);

		ArrayList<String> available = new ArrayList<String>();
		ArrayList<String> unavailable = new ArrayList<String>();

		/* First, make sure that the directory structure we need exists
		 * There should be a "cg" folder inside the flite data directory
		 * which will store all the clustergen voice data files.
		 */

		if(!Utility.pathExists(FLITE_DATA_PATH+"cg")) {
			// Create the directory.
			Log.e(LOG_TAG, "Flite data directory missing. Trying to create it.");
			boolean success;
			try {
				Log.e(LOG_TAG,FLITE_DATA_PATH);
				success = new File(FLITE_DATA_PATH+"cg").mkdirs();
			}
			catch (Exception e) {
				Log.e(LOG_TAG,"Could not create directory structure. "+e.getMessage());
				success = false;
			}

			if(!success) {
				Log.e(LOG_TAG, "Failed");
				// Can't do anything without appropriate directory structure.
				result = TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL;
				setResult(result, returnData);
				finish();
			}
		}

		/* Connect to CMU TTS server and get the list of voices available, 
		 * if we don't already have a file. 
		 */
		String voiceListFile = FLITE_DATA_PATH+"cg/voices.list";
		if(!Utility.pathExists(voiceListFile)) {
			Log.e(LOG_TAG, "Voice list file doesn't exist. Try getting it from server.");
			String voiceListURL = "http://tts.speech.cs.cmu.edu/android/vox-flite-1.5.6/voices.list?q=1";

			FileDownloader fdload = new FileDownloader();
			fdload.saveUrlAsFile(voiceListURL, voiceListFile);
			while(!fdload.finished) {}
			boolean savedVoiceList = fdload.success;

			if(!savedVoiceList)
				Log.w(LOG_TAG,"Could not update voice list from server");
			else
				Log.w(LOG_TAG,"Successfully updated voice list from server");
		}

		/* At this point, we MUST have a voices.list file. If this file is not there,
		 * possibly because internet connection was not available, we must create a dummy
		 * 
		 */
		if(!Utility.pathExists(FLITE_DATA_PATH+"cg/voices.list")) {
			try {
				Log.w(LOG_TAG, "Voice list not found, creating dummy list.");
				BufferedWriter out = new BufferedWriter(new FileWriter(FLITE_DATA_PATH+"cg/voices.list"));
				out.write("eng-USA-male,rms");
				out.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "Failed to create voice list dummy file.");
				// Can't do anything without that file.
				result = TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL;
				setResult(result, returnData);
				finish();
			}
		}
		/* Go through each line in voices.list file and see
		 * if the data for that voice is installed.
		 */

		ArrayList<String> voiceList = null;
		try {
			voiceList = Utility.readLines(FLITE_DATA_PATH+"cg/voices.list");
		} catch (IOException e) {
			Log.e(LOG_TAG,"Problem reading voices list. This shouldn't happen!");
			result = TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL;
			setResult(result, returnData);
			finish();
		}

		for(String strLine:voiceList) {
			String[] voiceInfo = strLine.split("\t");
			if (voiceInfo.length != 2) {
				Log.e(LOG_TAG, "Voice line could not be read: " + strLine);
				continue;
			}
			String voiceName = voiceInfo[0];
			String voiceMD5 = voiceInfo[1];
			
			String[] voiceParams = voiceName.split("-");
			if(voiceParams.length != 3) {
				Log.e(LOG_TAG,"Incorrect voicename:" + voiceName);
				continue;
			}

			if(voiceAvailable(voiceParams, voiceMD5)) {
				available.add(voiceName);
			} else {
				unavailable.add(voiceName);
			}
		}

		returnData.putStringArrayListExtra("availableVoices", available);
		returnData.putStringArrayListExtra("unavailableVoices", unavailable);
		setResult(result, returnData);
		finish();
	}

	public static boolean voiceAvailable(String[] voiceParams, String voiceMD5) {
		Log.v(LOG_TAG, "Checking for Voice Available: " + voiceParams[0]+"/"+voiceParams[1]+"/"+voiceParams[2]+".cg.flitevox");
		String voxdataFileName = FLITE_DATA_PATH + "cg/"+voiceParams[0]+"/"+voiceParams[1]+"/"+voiceParams[2]+".cg.flitevox";
		
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			Log.e(LOG_TAG, "MD5 could not be computed");
			return false;
		}
		FileInputStream fis;
		try {
			fis = new FileInputStream(voxdataFileName);
		}
		catch (FileNotFoundException e) {
			Log.e(LOG_TAG, "Voice File not found: " + voxdataFileName);
			return false;
		}
		byte[] dataBytes = new byte[1024];
		int nread = 0;
		try {
			while ((nread = fis.read(dataBytes)) != -1) {
				md.update(dataBytes, 0, nread);
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "Could not read voice file: " + voxdataFileName);
		}
		
		byte[] mdbytes = md.digest();	
		
		StringBuffer sb = new StringBuffer();
        for (int i = 0; i < mdbytes.length; i++) {
          sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        
		if (sb.toString().equals(voiceMD5)) {
			return true;
		}
		else {
			Log.e(LOG_TAG,"Voice file found, but MD5 sum incorrect. Found" +
					sb.toString() + ". Expected: " + voiceMD5);
			return false;
		}
		
		
	}

}  
