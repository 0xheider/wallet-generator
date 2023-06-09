package com.heider.generator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class GenerateCore {


    private String prefix;
    private String include;

    private String suffix;

    private int threads;

    @Value("${wallet.saveFile}")
    private Boolean saveFile;

    public ThreadPoolExecutor executor;

    private static File file = new File("." + File.separator + "wallet.dat");

    static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${threads}")
    public void setExecutor(Integer threads) {
        this.threads = threads;
        this.executor = new ThreadPoolExecutor(threads, threads, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<>(threads * 1000), new CustomizableThreadFactory("generator-"));
    }

    @Value("${wallet.include}")
    public void setInclude(String include) {
        this.include = include.trim().toLowerCase();
        if (!isHexNumber(this.include)) {
            log.error("Wrong Hex Number! (include)");
            throw new RuntimeException("Wrong Hex Number! (include)");
        }
    }

    @Value("${wallet.prefix}")
    public void setPrefix(String prefix) {
        this.prefix = "0x" + prefix.trim().toLowerCase();
        if (!isHexNumber(prefix.trim().toLowerCase())) {
            log.error("Wrong Hex Number! (prefix)");
            throw new RuntimeException("Wrong Hex Number! (prefix)");
        }
    }

    @Value("${wallet.suffix}")
    public void setSuffix(String suffix) {
        this.suffix = suffix.trim().toLowerCase();
        if (!isHexNumber(this.suffix)) {
            log.error("Wrong Hex Number! (suffix)");
            throw new RuntimeException("Wrong Hex Number! (suffix)");
        }
    }

    private static boolean isHexNumber(String str) {
        if (StringUtils.isBlank(str)) {
            return true;
        }
        boolean flag = true;
        for (int i = 0; i < str.length(); i++) {
            char cc = str.charAt(i);
            if (cc == '0' || cc == '1' || cc == '2' || cc == '3' || cc == '4' || cc == '5' || cc == '6' || cc == '7' || cc == '8' || cc == '9' || cc == 'A' || cc == 'B' || cc == 'C' ||
                    cc == 'D' || cc == 'E' || cc == 'F' || cc == 'a' || cc == 'b' || cc == 'c' || cc == 'c' || cc == 'd' || cc == 'e' || cc == 'f') {
            } else {
                flag = false;
                break;
            }
        }
        return flag;
    }

    public void generate() {
        AtomicInteger execTimes = new AtomicInteger(0);
        Integer lastPrintTime = 0;
        while (true) {
            BlockingQueue<Runnable> queue = executor.getQueue();
            if (queue.size() >= (threads * 500)) {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                }
                continue;
            }
            if (LocalTime.now().getSecond() != lastPrintTime) {
                lastPrintTime = LocalTime.now().getSecond();
                log.info("已尝试生成钱包次数：{}", execTimes.get());
            }
            executor.submit(() -> {
                String random = RandomStringUtils.random(64, "0123456789abcdef");
                Credentials credentials = Credentials.create(random);
                String address = credentials.getAddress();
                if ((StringUtils.isNotBlank(prefix) ? address.startsWith(prefix) : true)
                        && (StringUtils.isNotBlank(include) ? address.contains(include) : true)
                        && (StringUtils.isNotBlank(suffix) ? address.endsWith(suffix) : true)) {
                    BigInteger privateKey = credentials.getEcKeyPair().getPrivateKey();
                    String privateKeyHex = "0x" + privateKey.toString(16);
                    log.info("恭喜您，钱包已生成！");
                    log.info("address:{}", address);
                    //log.info("mnemonic:{}", mnemonic);
                    if (saveFile) {
                        save(address, privateKeyHex);
                    } else {
                        log.info("privateKey:{}", privateKeyHex);
                    }
                }
                execTimes.incrementAndGet();
            });
        }
    }

    public synchronized static void save(String address, String privateKeyHex) {
        try {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("生成时间:" + LocalDateTime.now().format(dateTimeFormatter));
            lines.add("address:" + address);
            //lines.add("mnemonic:" + mnemonic);
            lines.add("privateKey:" + privateKeyHex);
            lines.add("---------------------------------------------------------------------------------------");
            FileUtils.writeLines(file, lines, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
