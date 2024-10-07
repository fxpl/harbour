#include <jni.h>
#include <stdlib.h>
#include <stdio.h>

JNIEXPORT jint JNICALL Java_Yacht_runYcsbNative(JNIEnv *env, jobject obj, jstring cmdStr) {
    // Convert jstring to C string
    const char *cmd = (*env)->GetStringUTFChars(env, cmdStr, NULL);
    if (cmd == NULL) {
        return -1; // Out of memory
    }

    // Print the command (for debugging)
    fprintf(stderr, "Executing command: %s\n", cmd);

    // Execute the command
    int result = system(cmd);

    // Release the C string
    (*env)->ReleaseStringUTFChars(env, cmdStr, cmd);

    // Return the result code of the command
    return result;
}

