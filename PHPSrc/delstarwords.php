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
$sqlstr = "delete from tblstarwords where userid = ".$_GET["userid"]." and voc = '".$_GET["voc"]."'";
$conn->query($sqlstr);
echo "done";
$conn->close();
?>