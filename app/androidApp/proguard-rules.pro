# Keep source locations so release crashes can be retraced using mapping.txt.
-keepattributes SourceFile,LineNumberTable

# PDFBox's optional JP2Android codec is not installed. PDFBox only reads text and
# structure here; Android PdfRenderer performs all image/scan rasterization.
# Keep missing-class checking enabled for every other dependency.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
