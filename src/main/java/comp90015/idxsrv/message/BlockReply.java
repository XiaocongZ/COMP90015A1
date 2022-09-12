package comp90015.idxsrv.message;

public class BlockReply extends Message{
    @JsonElement
    public String fileName;

    @JsonElement
    public String fileMd5;

    @JsonElement
    public Integer blockIdx;

    @JsonElement
    public String bytes;

    public BlockReply(String fileName, String fileMd5, Integer blockIdx, String bytes){
        this.fileName = fileName;
        this.fileMd5 = fileMd5;
        this.blockIdx = blockIdx;
        this.bytes = bytes;
    }
}
