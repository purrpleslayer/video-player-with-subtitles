const startButton = document.getElementById("startButton");
const stopButton = document.getElementById("stopButton");
const recordingIndicator = document.getElementById("recordingIndicator");
const transcription = document.getElementById("transcription");


let mediaRecorder;
let audioChunks;
let currentStream;


startButton.addEventListener("click", async function(){
	
	const stream = await getAudioStream();
	
	if (stream === null) {
		recordingIndicator.textContent = "Microphone access failed.";
		return;
	}

	// Keep track of current mic stream
	currentStream = stream;
	// Clear audio from any previous recording
	audioChunks = [];

	// Create a new recorder 
	mediaRecorder = new MediaRecorder(stream);
	console.log("Recorder MIME type:", mediaRecorder.mimeType);

	// Store each piece of recorded audio
	mediaRecorder.addEventListener("dataavailable", function (event){
		audioChunks.push(event.data);
	});

	
	mediaRecorder.addEventListener("stop", async function() {

		const audioType = mediaRecorder.mimeType || audioChunks[0].type;
		const audioBlob = new Blob(audioChunks, {
			type: audioType
		});

		console.log("Blob size:", audioBlob.size);
		console.log("Blob type:", audioBlob.type);

		currentStream.getTracks().forEach(function(track) {
			track.stop();
		});

		await sendAudioToBackend(audioBlob);
	});

	mediaRecorder.start();

	startButton.disabled = true;
	stopButton.disabled = false;
	recordingIndicator.textContent = "Recording...";
	
});


stopButton.addEventListener("click", function(){

	mediaRecorder.stop();

	startButton.disabled = false;
	stopButton.disabled = true;
	recordingIndicator.textContent = "Recording stopped."
});


async function getAudioStream() {
	try {
		const stream = await navigator.mediaDevices.getUserMedia({
			video: false,
			audio: true		
		});	
		return stream;	
		
	}catch (error){
		console.error("Microphone access failed", error);
		return null;
	}
};

async function sendAudioToBackend(audioBlob) {

    const formData = new FormData();

	// Put audio blob into the form
	let fileName = "recording.webm";

	if (audioBlob.type.includes("ogg")) {
		fileName = "recording.ogg";
	} else if (audioBlob.type.includes("mp4")) {
		fileName = "recording.mp4";
	}

    formData.append("file", audioBlob, fileName);

    try {

		// Send audio to Spring endpoit
        const response = await fetch("/api/transcribe", {
            method: "POST",
            body: formData
        });

		// Read the text returned by SPring
        const result = await response.text();

        transcription.textContent = result;

    } catch (error) {

        console.error("Audio upload failed:", error);
    }
}

