/*
* 自己实现的环形缓存，用来存贮byte数组
* 20191018 by qiuxu
* 有加锁，可以异步多线程读写
* */
package hikvision.zhanyun.com.hikvision.utils;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class ArrayListBuffer {
    private ArrayList<byte[]> buf;
    private int read_offset;  // 写环形缓存的位置，大于缓存的size后置0
    private int write_offset; // 读环形缓存的位置，大于缓存的size后置0
    private int size; // 缓存的最大size
    private long write_total_offset; // 写数据的总数，不受缓存size影响
    private long read_total_offset; // 读数据的总数，不受缓存size影响
    private ReentrantLock lock = new ReentrantLock();

    public ArrayListBuffer(int len)
    {
        lock.lock();
        buf = new ArrayList<byte[]>();
        read_offset = 0;
        write_offset = 0;
        read_total_offset = 0;
        write_total_offset = 0;
        size = len;
        lock.unlock();
    }

    public boolean write(byte[] data)
    {
        lock.lock();
        if (buf == null){
            lock.unlock();
            return false;
        }
        if(write_total_offset > read_total_offset + size){
            // 如果写数据的位置大于读数据位置 + 环形缓存的大小，则不可写，写数据会覆盖掉还没有读的数据，需要等待
            lock.unlock();
            return false;
        }
        if (write_offset >= size) // 如果写数据位置大于size，则从头开始写
            write_offset = 0;
        if (write_total_offset < size)
            buf.add(data);
        else
            buf.set(write_offset,data);  // 环形缓存，当写数据包总数大于size时，覆盖掉开始的数据
        write_offset++;
        write_total_offset++;
        lock.unlock();
        return true;
    }

    public byte[] read()
    {
        lock.lock();
        if (buf == null || buf.size() == 0){
            lock.unlock();
            return null;
        }
        if (read_total_offset >= write_total_offset){
            // 如果读数据总数的位置大于写总数的位置，则需要等待
            lock.unlock();
            return null;
        }
        if (read_offset >= size) // 如果读数据位置大于size，则从头开始读
            read_offset = 0;
        byte[] data = buf.get(read_offset);
        read_offset++;
        read_total_offset++;
        lock.unlock();
        return data;
    }

    public void clear()
    {
        lock.lock();
        buf.clear();
        read_offset = 0;
        write_offset = 0;
        read_total_offset = 0;
        write_total_offset = 0;
        lock.unlock();
    }
}
