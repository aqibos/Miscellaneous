/**
 *
 * @author Aqib
 */
public class dnsresolver
{   
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        // TODO code application logic here
    }
    
    private myUdpPacket toSend;
    private myUdpPacket toRecv;
    
    public static final int DNS_PORT = 53;
    
    public dnsresolver()
    {
        
    }
    
    public myUdpPacket getToSendPacket()
    {
        return toSend;
    }
    
    public myUdpPacket getToRecvPacket()
    {
        return toRecv;
    }
    
    public class myUdpPacket 
    {
        private int id;
        private String domain;
        private int currIndex;
        private int numQuestion;
        private dnsResponse question;
        private int numAnswer;
        private dnsResponse [] allAnswers;
        private int numAuthority;
        private dnsResponse [] allAuthority;
        private int numAdditional;
        private dnsResponse [] allAdditional;
        private String fullToSendMessage;
        private String fullToRecvMessage;
        private String formattedToRecvMessage;
        
        public myUdpPacket(int id, String domain)
        {
            this.id = id;
            this.domain = domain;
        }
        
        private void configureToSendMessage()
        {
            fullToSendMessage = pad4( toHex(id))
                    + "0000"
                    + "0001"
                    + "0000"
                    + "0000"
                    + "0000"
                    + configureDomain(domain)
                    + "01"
                    + "0001";
        }
        
        private String configureDomain(String val)
        {
            String formattedDomain = "";
            String [] domainParts = val.split("\\.");
            
            for (String subDomain : domainParts)
            {
                formattedDomain += pad2( toHex( subDomain.length()));
                
                for (int i = 0; i < subDomain.length(); i++)
                {
                    formattedDomain += toHex( subDomain.charAt(i));
                }
            }
            
            return formattedDomain;
        }
        
        public String getToSendMessage()
        {
            return fullToSendMessage;
        }
        
        public String toString()
        {
            return formattedToRecvMessage;
        }
        
        
        // --------------- RECEIVING MESSAGE ---------------------
        
        
        
        public myUdpPacket(byte [] binaryResponse)
        {
            currIndex = 0;
            fullToRecvMessage = byteArrayToString(binaryResponse);
            numQuestion = extractNumQuestions();
            numAnswer = extractNumAnswers();
            allAnswers = new dnsResponse[numAnswer];
            numAuthority = extractNumAuthority();
            allAuthority = new dnsResponse[numAuthority];
            numAdditional = extractNumAdditional();
            allAdditional = new dnsResponse[numAdditional];
            
            if (numQuestion > 0)
                extractFullQuestions();
            if (numAnswer > 0)
            {
                for (int i = 0; i < allAnswers.length; i++)
                    extractFullAnswers();
            }
            if (numAuthority > 0)
            {
                for (int i = 0; i < allAuthority.length; i++)
                    extractFullAuthority();
            }
            if (numAdditional > 0)
            {
                for (int i = 0; i < allAdditional.length; i++)
                    extractFullAdditional();
            }
        }
        
        private int extractNumQuestions()
        {
            return binaryStringToInt(fullToRecvMessage.substring(32, 48));
        }
        
        private int extractNumAnswers()
        {
            return binaryStringToInt(fullToRecvMessage.substring(48, 64));
        }
        
        private int extractNumAuthority()
        {
            return binaryStringToInt(fullToRecvMessage.substring(64, 80));
        }
        
        private int extractNumAdditional()
        {
            return binaryStringToInt(fullToRecvMessage.substring(80, 96));
        }
        
        private String extractLabelFormat(int index)
        {
            //int copyCurrIndex = currIndex;
            int endIndex;
            int firstByteIndex = index;
            int secondByteIndex = index + 8;
            
            while (! (checkNBits('0', firstByteIndex, 8) && checkNBits('0', secondByteIndex, 8)))
            {
                firstByteIndex += 8;
                secondByteIndex += 8;
            }
            
            endIndex = secondByteIndex + 8;
            String address = fullToRecvMessage.substring(index, endIndex);
            
            currIndex = endIndex;
            return extractRestLabelFormat(address);
        }
        
        private String extractRestLabelFormat(String address)
        {
            int currLength;
            String domain = "";
            for (int i = 0; i < address.length();)
            {
                currLength = binaryStringToInt(address.substring(i + i + 8));
                i += 8;
                
                for (int j = 0; j < currLength; j++)
                {
                    if (i >= address.length() - 8)
                        break;
                    
                    domain += (char) binaryStringToInt(address.substring(i, i + 8));
                    i += 8;
                }
                
                domain += ".";
                
                if (i >= address.length() - 8)
                        break;
            }
            
            if (domain.length() > 1)
                domain = domain.substring(0, domain.length() - 2);
            
            return domain;
        }
        
        private String extractComboFormat(int index)
        {
            boolean isOffset = false;
            int firstByte = index;
            String address = "";
            int offset;
            
            while (!checkNBits('0', firstByte, 8))
            {
                if (!checkNBits('1', firstByte, 2))
                {
                    isOffset = true;
                    break;
                } 
                
                firstByte += 8;
            }
            
            if (isOffset)
            {
                address = extractRestLabelFormat(fullToRecvMessage.substring(index, firstByte));
                offset = 8 * binaryStringToInt(fullToRecvMessage.substring(firstByte + 2, firstByte  + 16));
                address += extractLabelFormat(offset);
                currIndex = firstByte + 16;
                
            }
            else
            {
                
            }
            
            return address;
        }
        
        private boolean checkNBits(char bit, int beginIndex, int numBits)
        {
            for (int i = beginIndex; i < beginIndex + numBits; i++)
            {
                if (fullToRecvMessage.charAt(i) != bit)
                    return false;
            }
            
            return true;
        }
        
        private void extractFullQuestions()
        {
            String name = extractLabelFormat(currIndex);
            int type = binaryStringToInt(fullToRecvMessage.substring(currIndex, currIndex + 8));
            currIndex += 8;
            String value = toHex(binaryStringToInt(fullToRecvMessage.substring(currIndex, currIndex + 16)));
            currIndex += 16;
            question = new dnsResponse(name, value, type);
        }
        
        private void extractFullAuthority()
        {
            //Pointer/name compression format
            if (checkNBits('1', currIndex, 2))
            {
                String address;
                
                int offset = 8 * 
                        binaryStringToInt(fullToRecvMessage.substring(currIndex + 2, currIndex + 16));
                currIndex += 16;
                
                address = extractLabelFormat(offset);
                String type = extractNBits(currIndex, 16);
                currIndex += 16;
                currIndex += 16;        //Skip class
                currIndex += 32;        //Skip time-to-live
                currIndex += 16;        //Skip length
                String data = extractComboFormat(currIndex);
                
                
            }
        }
        
        private void extractFullAdditional()
        {
            
        }
        
        private void extractFullAnswers()
        {
            
        }
        
        private String extractNBits(int beginIndex, int endIndex)
        {
            return fullToRecvMessage.substring(beginIndex, endIndex);
        }
    }
    
    public class dnsResponse
    {
        private String name;
        private String value;
        private int type;
        
        public dnsResponse(String name, String value, int type)
        {
            this.name = name;
            this.value = value;
            this.type = type;
        }
        
        public String getName()
        {
            return name;
        }
        
        public String getValue()
        {
            return value;
        }
        
        public int getType()
        {
            return type;
        }
    }
    
    public static String pad4(String val)
    {
        if (val.length() == 1)
            return "000" + val;
        else if (val.length() == 2)
            return "00" + val;
        else if (val.length() == 1)
            return "0" + val;
        else 
            return val;
    }
    
    public static String pad2(String val)
    {
        if (val.length() == 1)
            return "0" + val;
        else
            return val;
    }
    
    public static int hexStringToInt(String val)
    {
        return Integer.parseInt(val, 16);
    }
    
    public static int binaryStringToInt(String val)
    {
        return Integer.parseInt(val, 2);
    }
    
    /**
     * Utility method: converts either a char or int into it's hex equivalent.
     * @param <E> the char or int wrapper class.
     * @param val the char or int.
     * @return the hex equivalent of the input.
     */
    public static <E> String toHex(E val)
    {
        if (val instanceof Integer)
        {
            return Integer.toHexString((Integer) val);
        }
        else
        {
            char ch = (Character) val;
            return Integer.toHexString((int) ch);
        }
    }
    
    public static byte [] hexStringToByteArray(String val)
    {
        int length = val.length();
        byte [] data = new byte[length / 2];
        
        for (int i = 0; i < length; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(val.charAt(i), 16) << 4)
                    + Character.digit(val.charAt(i+1), 16));
        }
        
        return data;
    }
    
    public String byteArrayToString(byte [] arr)
    {
        String fullMessage = "";
        
        for (int i = 0; i < arr.length; i++)
        {
            fullMessage += String.format("%8s", 
                    Integer.toBinaryString(arr[i] & 0xFF)).replace(' ', '0');
        }
        
        return fullMessage;
    }
}
