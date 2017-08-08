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
$sqlstr = "insert into tblstarwords (UserID, Voc, CDateTime, Reserved1, Reserved2) values (".$_GET["userid"].", '".$_GET["voc"]."', CURRENT_TIMESTAMP, '', '')";
$conn->query($sqlstr);
echo "done";
$conn->close();
?>