-- Drop files on this app to send them to the phone (Download/FromMac).
-- Double-click it to open Phone Browser (search, sort, copy both ways).
-- Everything it runs is inside the app, in Contents/Resources.

on resourcesPath()
	return POSIX path of (path to me) & "Contents/Resources/"
end resourcesPath

on open droppedItems
	set args to ""
	repeat with f in droppedItems
		set args to args & " " & quoted form of POSIX path of f
	end repeat
	try
		set out to do shell script quoted form of (my resourcesPath() & "phone") & " send" & args & " 2>&1"
		display notification (last paragraph of out) with title "Phone Transfer"
	on error errMsg
		display alert "Phone Transfer" message errMsg
	end try
end open

on run
	do shell script quoted form of (my resourcesPath() & "phone-browser")
end run
