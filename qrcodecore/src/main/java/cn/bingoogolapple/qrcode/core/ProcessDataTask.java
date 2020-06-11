package cn.bingoogolapple.qrcode.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

class ProcessDataTask extends AsyncTask<Void, Void, ScanResult> {
    private Camera mCamera;
    private byte[] mData;
    private boolean mIsPortrait;
    private String mPicturePath;
    private Bitmap mBitmap;
    private WeakReference<QRCodeView> mQRCodeViewRef;
    private static long sLastStartTime = 0;

    ProcessDataTask(Camera camera, byte[] data, QRCodeView qrCodeView, boolean isPortrait) {
        mCamera = camera;
        mData = data;
        mQRCodeViewRef = new WeakReference<>(qrCodeView);
        mIsPortrait = isPortrait;
    }

    ProcessDataTask(String picturePath, QRCodeView qrCodeView) {
        mPicturePath = picturePath;
        mQRCodeViewRef = new WeakReference<>(qrCodeView);
    }

    ProcessDataTask(Bitmap bitmap, QRCodeView qrCodeView) {
        mBitmap = bitmap;
        mQRCodeViewRef = new WeakReference<>(qrCodeView);
    }

    ProcessDataTask perform() {
        executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        return this;
    }

    void cancelTask() {
        if (getStatus() != Status.FINISHED) {
            cancel(true);
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        mQRCodeViewRef.clear();
        mBitmap = null;
        mData = null;
    }

    private ScanResult processData(QRCodeView qrCodeView) {
        if (mData == null) {
            return null;
        }

        int width = 0;
        int height = 0;
        byte[] data = mData;
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            Camera.Size size = parameters.getPreviewSize();
            width = size.width;
            height = size.height;

            if (mIsPortrait) {
                data = new byte[mData.length];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        data[x * height + height - y - 1] = mData[x + y * width];
                    }
                }
                int tmp = width;
                width = height;
                height = tmp;
            }

            //return qrCodeView.processData(data, width, height, false);

            //正常二维码
            ScanResult result = qrCodeView.processData(data, width, height, false);
            //反色二维码
            //ScanResult result1 = processData1(qrCodeView);

            if (result != null && !TextUtils.isEmpty(result.result)){

                return result;
            } else {
                ScanResult scanResult = processSpecialData(qrCodeView);
                if (scanResult != null && !TextUtils.isEmpty(scanResult.result)) {

                    return scanResult;
                } else {
                    return null;
                }
            }

        } catch (Exception e1) {
            e1.printStackTrace();
            try {
                if (width != 0 && height != 0) {
                    BGAQRCodeUtil.d("识别失败重试");
                    return qrCodeView.processData(data, width, height, true);
                } else {
                    return null;
                }
            } catch (Exception e2) {
                e2.printStackTrace();
                return null;
            }
        }
    }

    @Override
    protected ScanResult doInBackground(Void... params) {
        QRCodeView qrCodeView = mQRCodeViewRef.get();
        if (qrCodeView == null) {
            return null;
        }

        if (mPicturePath != null) {
            return qrCodeView.processBitmapData(BGAQRCodeUtil.getDecodeAbleBitmap(mPicturePath));
        } else if (mBitmap != null) {
            ScanResult result = qrCodeView.processBitmapData(mBitmap);
            mBitmap = null;
            return result;
        } else {
            if (BGAQRCodeUtil.isDebug()) {
                BGAQRCodeUtil.d("两次任务执行的时间间隔：" + (System.currentTimeMillis() - sLastStartTime));
                sLastStartTime = System.currentTimeMillis();
            }
            long startTime = System.currentTimeMillis();

            ScanResult scanResult = processData(qrCodeView);

            //反色二维码
            //ScanResult result1 = processData1(qrCodeView);

            if (BGAQRCodeUtil.isDebug()) {
                long time = System.currentTimeMillis() - startTime;
                if (scanResult != null && !TextUtils.isEmpty(scanResult.result)) {
                    BGAQRCodeUtil.d("识别成功时间为：" + time);
                } else {
                    BGAQRCodeUtil.e("识别失败时间为：" + time);
                }
            }

            return scanResult;

//            if (BGAQRCodeUtil.isDebug()) {
//                long time = System.currentTimeMillis() - startTime;
//                if (scanResult != null && !TextUtils.isEmpty(scanResult.result)) {
//                    BGAQRCodeUtil.d("识别成功时间为：" + time);
//                } else if (result1 != null && !TextUtils.isEmpty(result1.result)) {
//                    BGAQRCodeUtil.d("识别成功时间为：" + time);
//                } else {
//                    BGAQRCodeUtil.e("识别失败时间为：" + time);
//                }
//            }

//            if(scanResult != null && !TextUtils.isEmpty(scanResult.result)){
//
//                return scanResult;
//            }
//
//            if (result1 != null && !TextUtils.isEmpty(result1.result)){
//
//                return result1;
//            }
//
//            return  null;

        }
    }

    @Override
    protected void onPostExecute(ScanResult result) {
        QRCodeView qrCodeView = mQRCodeViewRef.get();
        if (qrCodeView == null) {
            return;
        }

        if (mPicturePath != null || mBitmap != null) {
            mBitmap = null;
            qrCodeView.onPostParseBitmapOrPicture(result);
        } else {
            qrCodeView.onPostParseData(result);
        }
    }

    private ScanResult processSpecialData(QRCodeView qrCodeView) {
        if (mData == null) {
            return null;
        }
        int width = 0;
        int height = 0;
        byte[] data = mData;
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            Camera.Size size = parameters.getPreviewSize();
            width = size.width;
            height = size.height;
            //  Bitmap bitmap = BitmapFactory.decodeByteArray(mData, 0, mData.length);

            BitmapFactory.Options newOpts = new BitmapFactory.Options();
            newOpts.inJustDecodeBounds = true;

            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, bos);// 80--JPG图片的质量[0-100],100最高byte[] rawImage = baos.toByteArray();
            //将rawImage转换成bitmap

            BitmapFactory.Options options = new BitmapFactory.Options();

            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bos.toByteArray(), 0, bos.toByteArray().length, options);
            return qrCodeView.processBitmapData(reverseColor(bitmap));
        } catch (Exception e1) {
            e1.printStackTrace();
            try {
                if (width != 0 && height != 0) {
                    BGAQRCodeUtil.d("识别失败重试");
                    return qrCodeView.processData(data, width, height, true);
                } else {
                    return null;
                }
            } catch (Exception e2) {
                e2.printStackTrace();
                return null;
            }
        }
    }

    private Bitmap reverseColor(Bitmap bmp) {
        try {
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);//创建一个新的bitmap
            int[] pixs = new int[width * height];
            bmp.getPixels(pixs, 0, width, 0, 0, width, height);//把bitmap中的像素提取到pixs数组中去

            for (int i = 0; i < pixs.length; i++) {
                pixs[i] = pixs[i] ^ 0xffffffff;
            }
            bitmap.setPixels(pixs, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
