package comp90015.idxsrv.message;

public class BlockRequest extends Message{
    @JsonElement
    public String fileName;

    @JsonElement
    public String fileMd5;

    @JsonElement
    public Integer blockIdx;

    public BlockRequest(String fileName, String fileMd5, Integer blockIdx){
        this.fileName = fileName;
        this.fileMd5 = fileMd5;
        this.blockIdx = blockIdx;
    }
}
