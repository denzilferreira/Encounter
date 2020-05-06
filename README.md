Encounter - open cross-platform contact tracing solution
===============================================================
	
Encounter is a free, open-source, cross-platform (Android & iPhone) 
application that allows automated, private and secure contact tracing 
without servers or infrastructure deployment requirements. 
Encounter is an automated, privacy-aware, non-siloed co-presence contact tracing 
logging tool that could be useful to tackle the spread of this and 
future virus outbreaks.

![Overview](https://drive.google.com/uc?export=view&id=15OHn_xP-Ku7mAk4deIaRp5E77NfEA9-_)

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
