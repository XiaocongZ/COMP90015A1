package comp90015.idxsrv.message;

import java.util.Base64;
public class BlockReply extends Message{
    @JsonElement
    public String fileName;

    @JsonElement
    public String fileMd5;

    @JsonElement
    public Integer blockIdx;

    @JsonElement
    public String bytes;

    public BlockReply(String fileName, String fileMd5, Integer blockIdx, byte[] bytes){
        this.fileName = fileName;
        this.fileMd5 = fileMd5;
        this.blockIdx = blockIdx;

        this.bytes = Base64.getEncoder().encodeToString(bytes);
    }

    public byte[] getBytes(){
        return Base64.getDecoder().decode(bytes);
    }
}
