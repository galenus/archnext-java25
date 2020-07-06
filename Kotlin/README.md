## Instructions to compile the project to native image on Windows

The compilation was tested on Windows 10 with GraalVM 20.1.0 for Java 11 and VS2017 cross tools v14.16.2703 with C compiler (cl.exe) v19.16.27023.1 for x64.
Most of the instructions below are adapted from: https://www.graalvm.org/docs/getting-started/windows and https://www.graalvm.org/docs/reference-manual/native-image/.

- Download and extract the GraalVM image from https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-20.1.0/graalvm-ce-java11-windows-amd64-20.1.0.zip
- Update JAVA_HOME to point to the new JDK; add GraalVM's 'bin' path to the PATH variable.
- Install the native-image module using: `gu.cmd install native-image`.
- Copy the *native-image.exe* file from *{GraalVM root path}/lib/svm/bin* to *{GraalVM root path}/bin*.
- Make sure you have a recent version of Maven installed and accessible from command line.
- Make sure you have MSVC cross-tools installed (if there is a problem during the following compilation phase, try installing another version).
- Open the developer command prompt for the chosen MSVC version.
- Navigate to the root folder of this project in the command prompt.
- Run `mvn package` and wait for the completion of build. If the build fails, make sure you've completed all the steps in this list.
- Export two environment variables for the session: *BOT_NAME* with your bot name and *BOT_TOKEN* with the token you've received from Telegram BotFather.
- Run the *./target/j25triviabot.exe* program.

**Important Remark**:
Some antiviruses (Bitdefender, for example) may report the native image compilation by GraalVM as malicious activity and interrupt the process. 
