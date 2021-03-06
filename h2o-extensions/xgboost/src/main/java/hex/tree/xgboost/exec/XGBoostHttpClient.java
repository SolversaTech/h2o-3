package hex.tree.xgboost.exec;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import water.Key;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static water.util.HttpResponseStatus.OK;

public class XGBoostHttpClient {

    private static final Logger LOG = Logger.getLogger(XGBoostHttpClient.class);

    private final String baseUri;
    private final HttpClientBuilder clientBuilder;

    interface ResponseTransformer<T> {
        T transform(HttpEntity e) throws IOException;
    }

    private static final ResponseTransformer<byte[]> ByteArrayResponseTransformer = (e) -> {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copyStream(e.getContent(), bos);
        bos.close();
        byte[] b = bos.toByteArray();
        if (b.length == 0) return null;
        else return b;
    };

    private static final ResponseTransformer<XGBoostExecRespV3> JsonResponseTransformer = (e) -> {
        String responseBody = EntityUtils.toString(e);
        XGBoostExecRespV3 resp = new XGBoostExecRespV3();
        resp.fillFromBody(responseBody);
        return resp;
    };

    public XGBoostHttpClient(String baseUri, boolean https) {
        this.baseUri = (https ? "https" : "http") + "://" + baseUri + "/3/XGBoostExecutor.";
        this.clientBuilder = createClientBuilder(https);
    }

    private HttpClientBuilder createClientBuilder(boolean https) {
        try {
            HttpClientBuilder builder = HttpClientBuilder.create();
            if (https) {
                SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(TrustSelfSignedStrategy.INSTANCE)
                    .build();
                SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE
                );
                builder.setSSLSocketFactory(sslFactory);
            }
            return builder;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize HTTP client.", e);
        }
    }

    public XGBoostExecRespV3 postJson(Key key, String method, XGBoostExecReq reqContent) {
        return post(key, method, reqContent, JsonResponseTransformer);
    }

    public byte[] downloadBytes(Key key, String method, XGBoostExecReq reqContent) {
        return post(key, method, reqContent, ByteArrayResponseTransformer);
    }

    private <T> T post(Key key, String method, XGBoostExecReq reqContent, ResponseTransformer<T> transformer) {
        LOG.info("Request " + method + " " + key + " " + reqContent);
        XGBoostExecReqV3 req = new XGBoostExecReqV3(key, reqContent);
        HttpPost httpReq = new HttpPost(baseUri + method);
        httpReq.setEntity(new StringEntity(req.toJsonString(), UTF_8));
        httpReq.setHeader(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        return executeRequestAndReturnResponse(httpReq, transformer);
    }
    
    public XGBoostExecRespV3 uploadBytes(Key key, String dataType, byte[] data) {
        URIBuilder builder;
        HttpPost httpReq;
        try {
            builder = new URIBuilder(baseUri + "upload");
            builder.setParameter("model_key", key.toString())
                .setParameter("data_type", dataType);
            httpReq = new HttpPost(builder.build());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to build request URI.", e);
        }
        HttpEntity entity = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            .addBinaryBody("file", data, ContentType.DEFAULT_BINARY, "upload")
            .build();
        httpReq.setEntity(entity);
        return executeRequestAndReturnResponse(httpReq, JsonResponseTransformer);
    }

    private <T> T executeRequestAndReturnResponse(HttpPost req, ResponseTransformer<T> transformer) {
        try (CloseableHttpClient client = clientBuilder.build();
             CloseableHttpResponse response = client.execute(req)) {
            if (response.getStatusLine().getStatusCode() != OK.getCode()) {
                throw new IllegalStateException("Unexpected response (status: " + response.getStatusLine() + ").");
            }
            LOG.debug("Response received " + response.getEntity().getContentLength() + " bytes.");
            return transformer.transform(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException("HTTP Request failed", e);
        }
    }

}
