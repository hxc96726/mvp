package com.example.im_test.di.module;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.im_test.App;
import com.example.im_test.Utils.SpUtil;
import com.example.im_test.common.Constants;
import com.example.im_test.entity.UserBean;
import com.example.im_test.http.APIService;
import com.example.im_test.http.converter.CustomGsonConverterFactory;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;
import javax.net.ssl.SSLContext;

import dagger.Module;
import dagger.Provides;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

/**
 * @author Created by stone
 * @since 2018/7/10
 */
@Module
public class AppModule {

    /**
     * 默认超时时间 单位/秒
     */
    private static final int DEFAULT_TIME_OUT = 15;

    private Context context;

    private String baseUrl;
    private UserBean userBean;


    public AppModule(App context, String baseUrl) {
        this.context = context;
        this.baseUrl = baseUrl;
       ;
    }

    @Provides
    @Singleton
    public Context provideContext() {
        return context;
    }

    @Provides
    @Singleton
    public Retrofit provideRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(CustomGsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(provideOkHttpClient())
                .build();
    }

    @Provides
    @Singleton
    public OkHttpClient provideOkHttpClient() {
        OkHttpClient.Builder okHttpClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .cache(null)
                .connectTimeout(DEFAULT_TIME_OUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIME_OUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIME_OUT, TimeUnit.SECONDS)
                .addInterceptor(new MoreBaseUrlInterceptor());
        return enableTls12OnPreLollipop(okHttpClient).build();
    }

    /**
     * 兼容android4.x不支持SSL 导致https协议无法访问网络的问题
     *
     * @return
     */
    public static OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
            try {
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));

                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build();

                List<ConnectionSpec> specs = new ArrayList<>();
                specs.add(cs);
                specs.add(ConnectionSpec.COMPATIBLE_TLS);
                specs.add(ConnectionSpec.CLEARTEXT);

                client.connectionSpecs(specs);
            } catch (Exception exc) {
                Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2", exc);
            }
        }

        return client;
    }

    @Provides
    @Singleton
    public APIService provideAPIService() {
        return provideRetrofit().create(APIService.class);
    }

    /**
     * 开启okhttp打印连接日志
     *
     * @return
     */
    private HttpLoggingInterceptor getHttpLoggingInterceptor() {
        boolean isDebug = true;//是否开启
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();

        if (isDebug) {
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        }

        //开启打印连接日志
        return interceptor;
    }

    public class MoreBaseUrlInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            userBean= SpUtil.getObject(context, Constants.UserBean);
            //获取原始的originalRequest
            Request originalRequest = chain.request();
            //获取老的url
            HttpUrl oldUrl = originalRequest.url();
            //获取originalRequest的创建者builder
            Request.Builder builder = originalRequest.newBuilder();
            if(userBean!=null){
                builder.addHeader("token",userBean.getAccess_token());
            }
            //获取头信息的集合如：manage,mdffx
            List<String> urlnameList = originalRequest.headers("name");
            if (urlnameList != null && urlnameList.size() > 0) {
                //删除原有配置中的值,就是namesAndValues集合里的值
                builder.removeHeader("name");


                //获取头信息中配置的value,如：manage或者mdffx
                String urlname = urlnameList.get(0);
                HttpUrl baseURL = HttpUrl.parse(Constants.BASE_URL);

                //重建新的HttpUrl，需要重新设置的url部分
                HttpUrl newHttpUrl = oldUrl.newBuilder()
                        .scheme(baseURL.scheme())//http协议如：http或者https
                        .host(baseURL.host())//主机地址
                        .port(baseURL.port())//端口
                        //.setEncodedQueryParameter("sass_id", "-1")// 添加公共参数sass_id
                        .build();
                Log.e("请求url---->", newHttpUrl.url()+"");
                //获取处理后的新newRequest
                Request newRequest = builder.url(newHttpUrl).build();
                return chain.proceed(newRequest);
            } else {
                return chain.proceed(originalRequest);
            }

        }
    }
}
