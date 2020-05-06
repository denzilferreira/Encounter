Encounter - open cross-platform contact tracing solution
===============================================================
	
Encounter is a free, open-source, cross-platform (Android & iPhone) 
application that allows automated, private and secure contact tracing 
without servers or infrastructure deployment requirements. 
Encounter is an automated, privacy-aware, non-siloed co-presence contact tracing 
logging tool that could be useful to tackle the spread of this and 
future virus outbreaks.

![overview](https://drive.google.com/uc?export=view&id=15OHn_xP-Ku7mAk4deIaRp5E77NfEA9-_)

How is this different from DP3T & PEPP-PT
=========================================
- **Free**: ready to use, free Android and iOS applications, available in multiple languages
- **Truly decentralised**: there is no server receiving or analysing data
- **Voluntary end-to-end**: the only way to access your data is if you willingly share it
- **No hardware MAC exposure**: even though Bluetooth and WiFi are leveraged, there are no discoverable MAC addresses exchange for contact tracing
- **Privacy by design**: daily rotation of UUID (Unique Universal ID), reset on delete and on share of encounter data export JSON files
- **Autonomous**: the UUID fingerprinting occurs every 1 minute when the application is active, every 15 minutes on the background using Google Nearby API. No private information, no location
- **Useful contact tracing data**: JSON is easy to process, analyse, and integrate with many data science tools
- **Future-ready**: integrating Google+Apple protocol is possible when available to us

Encounter Data Format
=====================
The contact tracing data is stored in a JSON format that is readable by many tools. We chose a compact schema to minimize the file size for sharing.  
The data is recorded the same way, regardless of the country where the data originated.  
In case the device changes timezone, the readable format contains the currently active time as it was experience by the user in their current location.

### JSON
The data takes shape of a JSON Array of JSON Objects (a collection of all the encounters collected by the device):
* timestamp: UTC unixtime of the encounter in milliseconds
* uid: an incremental number for every sample that is recorded on the local database
* uuid: the active Encounter UUID, daily randomly generated
* uuid_detected: the Encounter UUID of someone that has also Encounter installed and is near you
* readable: a human readable conversion of the timestamp taking into account the users' timezone

It looks like this if you open the exported JSON file:
![json](https://drive.google.com/uc?export=view&id=1KoIZ_KyrMA9YMv28G2wjOJlzSDC7sObj)

When a user shares his data, the encounters data collected on the phone is exported to JSON file and saved in their smartphone internal and protected storage. 
These files, if shared, can then be opened in many data analysis tools such as Matlab, R, and others. 
You can share this JSON file with the authorities in your country over email, WhatsApp, or other applications that support file attachments. 
If you cancel the share, the file is discarded. A new UUID is assigned to you every time you export your data.

Offline, local database
=======================
The Encounter apps store the data only locally on your device. There is no server or infrastructure requirements to scale the usage of the app. 
Encounter can be used by any country and any individual with an Android or iPhone. The data can be collected indefinitely, as long as there is internal storage space on your smartphone. 
At any time, the user may clear the database, and in the process also reset the Encounter UUID. This is particularly useful to protect the privacy of those who are tested positively for COVID-19.

Open to contributions - using GitHub Issues tracker
===================================================
- **Bugs and issues**: found a bug or issue? Use GitHub issues to report them and we'll take a look to fix them
- **Open code contributions**: fork, edit, and do a pull-request to integrate your improvements or fixes
- **Open code review**: suggestions to improve Encounter functionality are welcome
- **Open tools for analysis**: make the encounters' data more useful for current and future pandemics
- **More languages**: download this [file](https://drive.google.com/file/d/1PA-gc1kNEfcsNCXV2UNdvq2yt2iaBYZc/view?usp=sharing), edit the text, and send it to [us](mailto:denzil.ferreira@oulu.fi?subject=[Encounter]%20New%20translation) 

Open-source (Apache 2.0)
========================
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at 
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
