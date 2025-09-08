import java.io.IOException;
import java.io.OutputStream;

/**
 * TeeOutputStream 将数据写入两个 OutputStream。
 * 类似于 UNIX 的 "tee" 命令。
 */
public class TeeOutputStream extends OutputStream {
    private final OutputStream out1;
    private final OutputStream out2;

    /**
     * 构造函数
     *
     * @param out1 第一个输出流
     * @param out2 第二个输出流
     */
    public TeeOutputStream(OutputStream out1, OutputStream out2) {
        this.out1 = out1;
        this.out2 = out2;
    }

    @Override
    public void write(int b) throws IOException {
        out1.write(b);
        out2.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out1.write(b);
        out2.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out1.write(b, off, len);
        out2.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out1.flush();
        out2.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            out1.close();
        } finally {
            out2.close();
        }
    }
}
