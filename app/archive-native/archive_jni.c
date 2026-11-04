/* Essentia's bounded read-only JNI bridge. GPL-2.0-or-later. Libarchive is BSD. */
#include <jni.h>
#include <archive.h>
#include <archive_entry.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

struct reader { struct archive *archive; struct archive_entry *entry; int count; };
static void error(JNIEnv *env, const char *message) {
    jclass exception=(*env)->FindClass(env,"java/io/IOException");
    if(exception)(*env)->ThrowNew(env,exception,message?message:"No se pudo leer el RAR");
}
JNIEXPORT jlong JNICALL Java_com_hatkid_mkxpz_NativeArchive_open(JNIEnv *env,jclass cls,jbyteArray path) {
    (void)cls;
    jsize length=(*env)->GetArrayLength(env,path);
    if(length<=0||length>32768){error(env,"Ruta no válida");return 0;}
    char *name=calloc((size_t)length+1,1);
    struct reader *r=calloc(1,sizeof(*r));
    if(!name||!r){free(name);free(r);error(env,"No hay memoria disponible");return 0;}
    (*env)->GetByteArrayRegion(env,path,0,length,(jbyte*)name);
    if((*env)->ExceptionCheck(env)){free(name);free(r);return 0;}
    r->archive=archive_read_new();
    if(!r->archive){free(name);free(r);error(env,"No hay memoria disponible");return 0;}
    archive_read_support_filter_none(r->archive);
    archive_read_support_format_rar(r->archive);
    archive_read_support_format_rar5(r->archive);
    int status=archive_read_open_filename(r->archive,name,65536);free(name);
    if(status!=ARCHIVE_OK){error(env,archive_error_string(r->archive));archive_read_free(r->archive);free(r);return 0;}
    return (jlong)(intptr_t)r;
}
JNIEXPORT jbyteArray JNICALL Java_com_hatkid_mkxpz_NativeArchive_next(JNIEnv *env,jclass cls,jlong handle) {
    (void)cls;struct reader *r=(struct reader*)(intptr_t)handle;
    for(;;){
        int status=archive_read_next_header(r->archive,&r->entry);
        if(status==ARCHIVE_EOF){
            if(r->count==0)error(env,"El RAR esta vacio o incompleto");
            return NULL;
        }
        if(status!=ARCHIVE_OK){error(env,archive_error_string(r->archive));return NULL;}
        if(++r->count>100000){error(env,"Demasiados archivos en el RAR");return NULL;}
        if(archive_entry_is_encrypted(r->entry)||archive_entry_symlink(r->entry)||archive_entry_hardlink(r->entry)){
            error(env,"Usa un RAR sin contraseña ni enlaces");return NULL;
        }
        if(archive_entry_filetype(r->entry)==AE_IFDIR)continue;
        if(archive_entry_filetype(r->entry)!=AE_IFREG){error(env,"Tipo de archivo no admitido");return NULL;}
        if(!archive_entry_size_is_set(r->entry)||archive_entry_size(r->entry)<0||archive_entry_size(r->entry)>16LL*1024*1024*1024){error(env,"Tamaño no permitido");return NULL;}
        const char *name=archive_entry_pathname_utf8(r->entry);
        if(!name)name=archive_entry_pathname(r->entry);
        if(!name||strlen(name)>32768){error(env,"Nombre de archivo no válido");return NULL;}
        jsize n=(jsize)strlen(name);jbyteArray result=(*env)->NewByteArray(env,n);
        if(result)(*env)->SetByteArrayRegion(env,result,0,n,(const jbyte*)name);
        return result;
    }
}
JNIEXPORT jlong JNICALL Java_com_hatkid_mkxpz_NativeArchive_size(JNIEnv *env,jclass cls,jlong handle) {
    (void)env;(void)cls;struct reader *r=(struct reader*)(intptr_t)handle;
    return (jlong)archive_entry_size(r->entry);
}
JNIEXPORT jint JNICALL Java_com_hatkid_mkxpz_NativeArchive_read(JNIEnv *env,jclass cls,jlong handle,jbyteArray bytes,jint offset,jint length) {
    (void)cls;struct reader *r=(struct reader*)(intptr_t)handle;
    if(offset<0||length<0||offset>(*env)->GetArrayLength(env,bytes)-length||length>65536){error(env,"Lectura no válida");return -1;}
    if(length==0)return 0;
    jbyte *data=(*env)->GetByteArrayElements(env,bytes,NULL);if(!data)return -1;
    la_ssize_t n=archive_read_data(r->archive,data+offset,(size_t)length);
    (*env)->ReleaseByteArrayElements(env,bytes,data,n>0?0:JNI_ABORT);
    if(n<0){error(env,archive_error_string(r->archive));return -1;}
    return n==0?-1:(jint)n;
}
JNIEXPORT void JNICALL Java_com_hatkid_mkxpz_NativeArchive_close(JNIEnv *env,jclass cls,jlong handle) {
    (void)env;(void)cls;struct reader *r=(struct reader*)(intptr_t)handle;
    if(r){archive_read_free(r->archive);free(r);}
}
