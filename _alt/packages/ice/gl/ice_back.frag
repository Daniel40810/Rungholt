// ice_back.frag — Rückseiten: Abstand vom Auge (m) in eine Gleitkommatextur; das Hauptbild liest daraus die Dicke.
in vec3 vW, vN, vL;
in float vAo;
uniform vec3 uEye;
out float outDist;
void main() { outDist = length(vW - uEye); }
