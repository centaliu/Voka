<?php
$servername = "localhost";
$username = "id1051297_centaliu";
$password = $_GET["dbpwd"];
$dbname = "id1051297_dbappdata";

// Create connection
$conn = new mysqli($servername, $username, $password, $dbname);
// Check connection
if ($conn->connect_error) {
    die("Connection failed: " . $conn->connect_error);
} 
$star = 0;
$rndInt = rand(1, 5);
if ($rndInt != 1)
{
    $sqlstr = "select id, voc, VoiceURL from tblvocbank where instr(tags, '".$_GET["tag"]."') > 0 order by rand() limit 1";
}
else 
{
    $star = 1;
    $sqlstr = "select tblvocbank.id as id, tblstarwords.voc as voc, tblvocbank.voiceurl as VoiceURL from tblstarwords
inner join tblvocbank on tblstarwords.voc = tblvocbank.voc
where userid = ".$_GET["userid"]." order by rand() limit 1";
}
$result = $conn->query($sqlstr);

if ($result->num_rows > 0) {
    // output data of each row
    while($row = $result->fetch_assoc()) {
        echo $row["id"]."~".$row["voc"]."~".$row["VoiceURL"]."~".$star;
    }
} else {
    echo "";
}
$conn->close();
?>