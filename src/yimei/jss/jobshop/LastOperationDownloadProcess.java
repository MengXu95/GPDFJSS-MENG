package yimei.jss.jobshop;

/**
 * Created by yimei on 22/09/16.
 */
public class LastOperationDownloadProcess implements Comparable<LastOperationDownloadProcess> {

    private WorkCenter fromWorkCenter;
    private OperationOption operationOption;
    private double startTime;
    private double finishTime;

    public LastOperationDownloadProcess(WorkCenter fromWorkCenter, OperationOption operationOption, double startTime) {
        this.fromWorkCenter = fromWorkCenter;
        this.operationOption = operationOption;
        this.startTime = startTime;
        this.finishTime = startTime + operationOption.getOperation().getDownloadTime();
    }

    public WorkCenter getFromWorkCenter() {
        return fromWorkCenter;
    }

    public OperationOption getOperationOption() {
        return operationOption;
    }

    public double getStartTime() {
        return startTime;
    }

    public double getFinishTime() {
        return finishTime;
    }

    public double getDuration() {
        return finishTime - startTime;
    }

    @Override
    public String toString() {
        return String.format("([Entry,W%d], [J%d,O%d,O%d]: %.1f --> %.1f.\n",
                fromWorkCenter.getId(), operationOption.getJob().getId(),
                operationOption.getOperation().getId(), operationOption.getOptionId(), startTime, finishTime);
    }

    @Override
    public int compareTo(LastOperationDownloadProcess other) {
        if (startTime < other.startTime)
            return -1;

        if (startTime > other.startTime)
            return 1;

        return 0;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LastOperationDownloadProcess process = (LastOperationDownloadProcess) o;

        if (fromWorkCenter.getId() != process.getFromWorkCenter().getId()) return false;
        if (Double.compare(process.startTime, startTime) != 0) return false;
        if (Double.compare(process.finishTime, finishTime) != 0) return false;
        if (fromWorkCenter != null ? !fromWorkCenter.equals(process.fromWorkCenter) : process.fromWorkCenter != null) return false;
        return operationOption != null ? operationOption.equals(process.operationOption) : process.operationOption == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = fromWorkCenter != null ? fromWorkCenter.hashCode() : 0;
        result = 31 * result + (operationOption != null ? operationOption.hashCode() : 0);
        temp = Double.doubleToLongBits(startTime);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(finishTime);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
