#include <jni.h>

#include <android/log.h>

#include <errno.h>

#include <fitz.h>
#include <mupdf.h>

/* Debugging helper */

#define DEBUG(args...) \
	__android_log_print(ANDROID_LOG_DEBUG, "PdfDroid", args)

#define ERROR(args...) \
	__android_log_print(ANDROID_LOG_ERROR, "PdfDroid", args)

#define INFO(args...) \
	__android_log_print(ANDROID_LOG_INFO, "PdfDroid", args)


typedef struct renderdocument_s renderdocument_t;
struct renderdocument_s
{
	pdf_xref *xref;
	pdf_outline *outline;
	fz_glyphcache *drawcache;
};

typedef struct renderpage_s renderpage_t;
struct renderpage_s
{
	pdf_page *page;
//New draw page
	fz_displaylist *pageList;
//
};

JNI_OnLoad(JavaVM *jvm, void *reserved)
{
	DEBUG("initializing PdfRender JNI library based on MuPDF");
	fz_accelerate();
	return JNI_VERSION_1_2;
}

#define RUNTIME_EXCEPTION "java/lang/RuntimeException"

void throw_exception(JNIEnv *env, char *message)
{
	jthrowable new_exception = (*env)->FindClass(env, RUNTIME_EXCEPTION);
	if(new_exception == NULL) {
		return;
	} else {
		DEBUG("Exception '%s', Message: '%s'", RUNTIME_EXCEPTION, message);
	}
	(*env)->ThrowNew(env, new_exception, message);
}

JNIEXPORT jlong JNICALL
	Java_org_vudroid_pdfdroid_codec_PdfDocument_open
	(JNIEnv *env, jclass clazz,
			jint fitzmemory, jstring fname, jstring pwd)
{
	fz_error error;
	fz_obj *obj;
	renderdocument_t *doc;
	jboolean iscopy;
	jclass cls;
	jfieldID fid;
	char *filename;
	char *password;

	filename = (char*)(*env)->GetStringUTFChars(env, fname, &iscopy);
	password = (char*)(*env)->GetStringUTFChars(env, pwd, &iscopy);

	doc = fz_malloc(sizeof(renderdocument_t));
	if(!doc) 
	{
		throw_exception(env, "Out of Memory");
		goto cleanup;
	}

	/* initialize renderer */

	doc->drawcache = fz_newglyphcache();
	if (!doc->drawcache) 
	{
		throw_exception(env, "Cannot create new renderer");
		goto cleanup;
	}

	/*
	 * Open PDF and load xref table
	 */
//	error = pdf_openxref(&(doc->xref), filename, password);
	error = pdf_openxref(&(doc->xref), filename, NULL);

	if (error || (!doc->xref)) 
	{
		throw_exception(env, "PDF file not found or corrupted");
		goto cleanup;
	}

	/*
	 * Handle encrypted PDF files
	 */

	if (pdf_needspassword(doc->xref)) 
	{
		if(strlen(password)) 
		{
			int ok = pdf_authenticatepassword(doc->xref, password);
			if(!ok) 
			{
				throw_exception(env, "Wrong password given");
				goto cleanup;
			}
		} 
		else 
		{
			throw_exception(env, "PDF needs a password!");
			goto cleanup;
		}
	}
	doc->outline = pdf_loadoutline(doc->xref);

	error = pdf_loadpagetree(doc->xref);
	if (error) 
	{
    	    	throw_exception(env, "error loading pagetree");
		goto cleanup;
	}
	
cleanup:

	(*env)->ReleaseStringUTFChars(env, fname, filename);
	(*env)->ReleaseStringUTFChars(env, pwd, password);

	DEBUG("PdfDocument.nativeOpen(): return handle = %p", doc);
	return (jlong) (long)doc;
}

JNIEXPORT void JNICALL
	Java_org_vudroid_pdfdroid_codec_PdfDocument_free
	(JNIEnv *env, jclass clazz, jlong handle)
{
	renderdocument_t *doc = (renderdocument_t*) (long)handle;

	if(doc) 
	{
		if(doc->outline)
		      pdf_freeoutline(doc->outline);
		doc->outline = nil;
		                          
		if(doc->xref->store)
		    pdf_freestore(doc->xref->store);
		doc->xref->store = nil;
			
		if (doc->drawcache) 
		    fz_freeglyphcache(doc->drawcache);
		doc->drawcache = nil;
		
		if(doc->xref)
		    pdf_freexref(doc->xref);
		doc->xref = nil;
		
		fz_free(doc);
		doc = nil;
	}
}

JNIEXPORT jint JNICALL
	Java_org_vudroid_pdfdroid_codec_PdfDocument_getPageCount
	(JNIEnv *env, jclass clazz, jlong handle)
{
	renderdocument_t *doc = (renderdocument_t*) (long)handle;
	return pdf_getpagecount(doc->xref);
}

JNIEXPORT jlong JNICALL
	Java_org_vudroid_pdfdroid_codec_PdfPage_open
	(JNIEnv *env, jclass clazz, jlong dochandle, jint pageno)
{
	renderdocument_t *doc = (renderdocument_t*) (long)dochandle;
	renderpage_t *page;
	fz_error error;
	fz_obj *obj;
	jclass cls;
	jfieldID fid;

	page = fz_malloc(sizeof(renderpage_t));
	if(!page) 
	{
		throw_exception(env, "Out of Memory");
		return (jlong) (long)NULL;
	}

	obj = pdf_getpageobject(doc->xref, pageno);
	error = pdf_loadpage(&page->page, doc->xref, obj);
	if (error) 
	{
		throw_exception(env, "error loading page");
		goto cleanup;
	}

//New draw page
	page->pageList = fz_newdisplaylist();
	fz_device *dev = fz_newlistdevice(page->pageList);
	pdf_runpage(doc->xref, page->page , dev, fz_identity);
	fz_freedevice(dev);
//

cleanup:
	/* nothing yet */

	DEBUG("PdfPage.nativeOpenPage(): return handle = %p", page);
	return (jlong) (long)page;
}

JNIEXPORT void JNICALL
	Java_org_vudroid_pdfdroid_codec_PdfPage_free
	(JNIEnv *env, jclass clazz, jlong handle)
{

	renderpage_t *page = (renderpage_t*) (long)handle;
	DEBUG("PdfPage_free(%p)",page);

	if(page) {
		if (page->page)
			pdf_freepage(page->page);
//New draw page
		if (page->pageList)
		    fz_freedisplaylist(page->pageList);
//
		fz_free(page);
	}
}


JNIEXPORT void JNICALL
	Java_org_vudroid_pdfdroid_codec_PdfPage_getMediaBox
	(JNIEnv *env, jclass clazz, jlong handle, jfloatArray mediabox)
{
	renderpage_t *page = (renderpage_t*) (long)handle;
	jfloat *bbox = (*env)->GetPrimitiveArrayCritical(env, mediabox, 0);
	if(!bbox) return;
//	DEBUG("Mediabox: %f %f %f %f", page->page->mediabox.x0, page->page->mediabox.y0, page->page->mediabox.x1, page->page->mediabox.y1);
	bbox[0] = page->page->mediabox.x0;
	bbox[1] = page->page->mediabox.y0;
	bbox[2] = page->page->mediabox.x1;
	bbox[3] = page->page->mediabox.y1;
	(*env)->ReleasePrimitiveArrayCritical(env, mediabox, bbox, 0);
}

JNIEXPORT void JNICALL
Java_org_vudroid_pdfdroid_codec_PdfPage_renderPage
	(JNIEnv *env, jobject this, jlong dochandle, jlong pagehandle,
		jintArray viewboxarray, jfloatArray matrixarray,
		jintArray bufferarray)
{
	renderdocument_t *doc = (renderdocument_t*) (long)dochandle;
	renderpage_t *page = (renderpage_t*) (long)pagehandle;
	DEBUG("PdfView(%p).renderPage(%p, %p)", this, doc, page);
	fz_error error;
	fz_matrix ctm;
	fz_bbox viewbox;
	fz_pixmap *pixmap;
	jfloat *matrix;
	jint *viewboxarr;
	jint *dimen;
	jint *buffer;
	int length, val;
	fz_device *dev = NULL;

	/* initialize parameter arrays for MuPDF */

	ctm = fz_identity;

	matrix = (*env)->GetPrimitiveArrayCritical(env, matrixarray, 0);
	ctm.a = matrix[0];
	ctm.b = matrix[1];
	ctm.c = matrix[2];
	ctm.d = matrix[3];
	ctm.e = matrix[4];
	ctm.f = matrix[5];
	(*env)->ReleasePrimitiveArrayCritical(env, matrixarray, matrix, 0);
	DEBUG("Matrix: %f %f %f %f %f %f", ctm.a, ctm.b, ctm.c, ctm.d, ctm.e, ctm.f);


	viewboxarr = (*env)->GetPrimitiveArrayCritical(env, viewboxarray, 0);
	viewbox.x0 = viewboxarr[0];
	viewbox.y0 = viewboxarr[1];
	viewbox.x1 = viewboxarr[2];
	viewbox.y1 = viewboxarr[3];

	(*env)->ReleasePrimitiveArrayCritical(env, viewboxarray, viewboxarr, 0);
	DEBUG("Viewbox: %d %d %d %d", viewbox.x0, viewbox.y0, viewbox.x1, viewbox.y1);
	/* do the rendering */

	buffer = (*env)->GetPrimitiveArrayCritical(env, bufferarray, 0);
	
	pixmap = fz_newpixmapwithdata(fz_devicebgr, viewbox.x0, viewbox.y0, viewbox.x1 - viewbox.x0, viewbox.y1 - viewbox.y0, (unsigned char*)buffer);

	DEBUG("doing the rendering...");
	
	fz_clearpixmapwithcolor(pixmap, 0xff);

//Old draw page
//	dev = fz_newdrawdevice(doc->drawcache, pixmap);
//	error = pdf_runpage(doc->xref, page->page, dev, ctm);
//	fz_freedevice(dev);
//

//New draw page
	dev = fz_newdrawdevice(doc->drawcache, pixmap);
        fz_executedisplaylist(page->pageList, dev, ctm);
        fz_freedevice(dev);
//

	(*env)->ReleasePrimitiveArrayCritical(env, bufferarray, buffer, 0);
	
	fz_droppixmap(pixmap);
//Old draw page
//	if (error) 
//	{
//		DEBUG("error!");
//		throw_exception(env, "error rendering page");
//	}
//
	DEBUG("PdfView.renderPage() done");
}
