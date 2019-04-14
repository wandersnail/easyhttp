package com.snail.network.upload

import com.snail.network.TaskWorker
import com.snail.network.callback.MultiTaskListener
import com.snail.network.callback.ProgressListener
import com.snail.network.callback.TaskListener
import com.snail.network.utils.HttpUtils
import com.snail.network.utils.SchedulerUtils
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.io.File
import java.net.URLEncoder

/**
 *
 *
 * date: 2019/2/28 18:21
 * author: zengfansheng
 */
class UploadWorker<R, T : UploadInfo<R>> : TaskWorker<R, T> {
    internal constructor(info: T, listener: TaskListener<T>?) : super(info, listener)

    internal constructor(infos: List<T>, listener: MultiTaskListener<T>?) : super(infos, listener)

    override fun execute(info: T) {
        //如果listener为空，说明不需要监听，不为空则在本地监听后，再传出去
        val observer = UploadObserver(info, if (listener == null) null else LocalTaskListener())
        taskMap[info] = observer
        val service = Retrofit.Builder()
            .client(HttpUtils.initHttpsClient(true, OkHttpClient.Builder()).build())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl(info.baseUrl)
            .build()
            .create(UploadService::class.java)
        val observable = if (info.args == null) {//不带参数
            service.upload(info.url, createFilePart(info.mediaType, info.file, observer))
        } else {//带参数
            service.upload(info.url, info.args, createFilePart(info.mediaType, info.file, observer))
        }
        HttpUtils.convertObservable(observable, info.converter)
            .compose(SchedulerUtils.applyGeneralObservableSchedulers())
            .subscribe(observer)
    }
    
    private fun createFilePart(mediaType: MediaType?, file: File, listener: ProgressListener?): MultipartBody.Part {
        return MultipartBody.Part.createFormData("file", URLEncoder.encode(file.name, "utf-8"),
                ProgressRequestBody(mediaType ?: MediaType.parse("multipart/form-data"), file, listener))
    }
}